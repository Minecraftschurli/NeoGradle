package net.neoforged.gradle.common.util;

import net.neoforged.gradle.common.runtime.definition.CommonRuntimeDefinition;
import net.neoforged.gradle.common.runtime.definition.IDelegatingRuntimeDefinition;
import net.neoforged.gradle.common.runtime.extensions.CommonRuntimeExtension;
import net.neoforged.gradle.common.runtime.extensions.RuntimesExtension;
import net.neoforged.gradle.common.util.exceptions.MultipleDefinitionsFoundException;
import net.neoforged.gradle.dsl.common.runtime.definition.Definition;
import org.gradle.api.Buildable;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.tasks.AbstractTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class TaskDependencyUtils {

    private TaskDependencyUtils() {
        throw new IllegalStateException("Can not instantiate an instance of: TaskDependencyUtils. This is a utility class");
    }

    public static Set<? extends Task> getDependencies(Task task) {
        final LinkedHashSet<? extends Task> dependencies = new LinkedHashSet<>();
        final LinkedList<Task> queue = new LinkedList<>();
        queue.add(task);

        getDependencies(queue, dependencies);

        return dependencies;
    }

    public static Set<? extends Task> getDependencies(Buildable task) {
        final LinkedHashSet<? extends Task> dependencies = new LinkedHashSet<>();
        final LinkedList<Task> queue = new LinkedList<>(task.getBuildDependencies().getDependencies(null));

        getDependencies(queue, dependencies);

        return dependencies;
    }

    @SuppressWarnings("unchecked")
    private static void getDependencies(final LinkedList<Task> queue, final Set<? extends Task> tasks) {
        if (queue.isEmpty())
            return;

        final Task task = queue.removeFirst();
        if (tasks.contains(task)) {
            if (queue.isEmpty()) {
                return;
            }

            getDependencies(queue, tasks);
            return;
        }

        ((Set<Task>) tasks).add(task);
        queue.addAll(task.getTaskDependencies().getDependencies(task));
        getDependencies(queue, tasks);
    }

    public static CommonRuntimeDefinition<?> realiseTaskAndExtractRuntimeDefinition(@NotNull Project project, TaskProvider<?> t) throws MultipleDefinitionsFoundException {
        return extractRuntimeDefinition(project, t.get());
    }

    public static CommonRuntimeDefinition<?> extractRuntimeDefinition(@NotNull Project project, Task t) throws MultipleDefinitionsFoundException {
        return validateAndUnwrapDefinitions(project, "task", t.getName(), findRuntimes(project, t));
    }

    public static CommonRuntimeDefinition<?> extractRuntimeDefinition(@NotNull Project project, TaskDependencyContainer files) throws MultipleDefinitionsFoundException {
        return validateAndUnwrapDefinitions(project, "task dependency container", files.toString(), findRuntimes(project, files));
    }

    public static CommonRuntimeDefinition<?> extractRuntimeDefinition(Project project, SourceSet sourceSet) throws MultipleDefinitionsFoundException {
        return validateAndUnwrapDefinitions(project, "source set", sourceSet.getName(), findRuntimes(project, sourceSet));
    }

    public static CommonRuntimeDefinition<?> extractRuntimeDefinition(Project project, Collection<SourceSet> sourceSets) throws MultipleDefinitionsFoundException {
        return validateAndUnwrapDefinitions(project, "source sets", sourceSets.stream().map(SourceSet::getName).collect(Collectors.joining(", ", "[", "]")), findRuntimes(project, sourceSets));
    }

    private static CommonRuntimeDefinition<?> validateAndUnwrapDefinitions(@NotNull Project project, String type, String name, Collection<? extends CommonRuntimeDefinition<?>> definitions) throws MultipleDefinitionsFoundException {
        if (definitions.isEmpty()) {
            throw new IllegalStateException(String.format("Could not find runtime definition for %s: %s", type, name));
        }
        final List<CommonRuntimeDefinition<?>> undelegated = unwrapDelegation(project, definitions);

        if (undelegated.size() != 1)
            throw new MultipleDefinitionsFoundException(undelegated);

        return undelegated.get(0);
    }

    private static Collection<? extends CommonRuntimeDefinition<?>> findRuntimes(Project project, Task t) {
        FileCollection files = t.getInputs().getFiles();
        if (files instanceof TaskDependencyContainer) {
            return findRuntimes(project, (TaskDependencyContainer) files);
        }
        return Collections.emptySet();
    }

    private static Collection<? extends CommonRuntimeDefinition<?>> findRuntimes(Project project, Collection<SourceSet> sourceSets) {
        RuntimeFindingTaskDependencyResolveContext context = new RuntimeFindingTaskDependencyResolveContext(project);
        sourceSets.forEach(context::add);
        return context.getRuntimes();
    }

    private static Collection<? extends CommonRuntimeDefinition<?>> findRuntimes(Project project, SourceSet sourceSet) {
        RuntimeFindingTaskDependencyResolveContext context = new RuntimeFindingTaskDependencyResolveContext(project);
        context.add(sourceSet);
        return context.getRuntimes();
    }

    private static Collection<? extends CommonRuntimeDefinition<?>> findRuntimes(Project project, TaskDependencyContainer files) {
        RuntimeFindingTaskDependencyResolveContext context = new RuntimeFindingTaskDependencyResolveContext(project);
        files.visitDependencies(context);
        return context.getRuntimes();
    }

    @SuppressWarnings("SuspiciousMethodCalls")
    private static List<CommonRuntimeDefinition<?>> unwrapDelegation(final Project project, final Collection<? extends CommonRuntimeDefinition<?>> input) {
        final List<CommonRuntimeDefinition<?>> output = new LinkedList<>();

        project.getExtensions().getByType(RuntimesExtension.class)
                .getAllDefinitions()
                .stream()
                .filter(IDelegatingRuntimeDefinition.class::isInstance)
                .map(runtime -> (IDelegatingRuntimeDefinition<?>) runtime)
                .filter(runtime -> input.contains(runtime.getDelegate()))
                .map(runtime -> (CommonRuntimeDefinition<?>) runtime)
                .forEach(output::add);

        final List<CommonRuntimeDefinition<?>> noneDelegated = input.stream()
                .filter(runtime -> output.stream()
                        .filter(IDelegatingRuntimeDefinition.class::isInstance)
                        .map(r -> (IDelegatingRuntimeDefinition<?>) r)
                        .noneMatch(r -> r.getDelegate().equals(runtime))).collect(Collectors.toList());
        output.addAll(noneDelegated);
        return output.stream().distinct().collect(Collectors.toList());
    }

    private static class RuntimeFindingTaskDependencyResolveContext extends AbstractTaskDependencyResolveContext {
        private final Set<Object> seen = new HashSet<>();
        private final Set<CommonRuntimeDefinition<?>> found = new HashSet<>();
        private final SourceSetContainer sourceSets;
        private final Collection<? extends Definition<?>> runtimes;
        @SuppressWarnings("unchecked")

        public RuntimeFindingTaskDependencyResolveContext(Project project) {
            this.sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            final CommonRuntimeExtension<?, ?, ? extends Definition<?>> runtimeExtension = project.getExtensions().getByType(CommonRuntimeExtension.class);
            this.runtimes = runtimeExtension.getRuntimes().get().values();
        }

        @Override
        public void add(@NotNull Object dependency) {
            if (!this.seen.add(dependency)) {
                return;
            }
            if (dependency instanceof CommonRuntimeDefinition<?>) {
                found.add((CommonRuntimeDefinition<?>) dependency);
            } else if (dependency instanceof SourceSet) {
                SourceSet sourceSet = (SourceSet) dependency;
                ExtraPropertiesExtension extraProperties = sourceSet.getExtensions().getExtraProperties();
                Object runtimeDefinition;
                if (extraProperties.has("runtimeDefinition") && (runtimeDefinition = extraProperties.get("runtimeDefinition")) != null) {
                    this.add(runtimeDefinition);
                } else {
                    Set<CommonRuntimeDefinition<?>> tmp = new HashSet<>(this.found);
                    this.found.clear();
                    this.add(sourceSet.getCompileClasspath());
                    if (this.found.size() == 1) {
                        extraProperties.set("runtimeDefinition", this.found.iterator().next());
                    }
                    this.found.addAll(tmp);
                }
            } else if (dependency instanceof SourceDirectorySet) {
                sourceSets.stream()
                        .filter(sourceSet ->
                                sourceSet.getAllJava() == dependency ||
                                sourceSet.getResources() == dependency ||
                                sourceSet.getJava() == dependency ||
                                sourceSet.getAllSource() == dependency)
                        .forEach(this::add);
            } else if (dependency instanceof JavaCompile) {
                this.add(((JavaCompile) dependency).getClasspath());
            } else if (dependency instanceof Configuration) {
                DependencySet dependencies = ((Configuration) dependency).getAllDependencies();
                runtimes.stream()
                        .filter(runtime -> dependencies.contains(runtime.getReplacedDependency()))
                        .map(runtime -> (CommonRuntimeDefinition<?>) runtime)
                        .forEach(this::add);
            } else if (dependency instanceof TaskDependencyContainer) {
                TaskDependencyContainer container = (TaskDependencyContainer)dependency;
                container.visitDependencies(this);
            }
        }

        @Nullable
        @Override
        public Task getTask() {
            return null;
        }

        public Collection<? extends CommonRuntimeDefinition<?>> getRuntimes() {
            return found;
        }
    }
}
