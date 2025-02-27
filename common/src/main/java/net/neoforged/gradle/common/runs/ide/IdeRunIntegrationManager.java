package net.neoforged.gradle.common.runs.ide;

import net.neoforged.gradle.common.extensions.IdeManagementExtension;
import net.neoforged.gradle.common.runs.ide.extensions.IdeaRunExtensionImpl;
import net.neoforged.gradle.common.runs.run.RunImpl;
import net.neoforged.gradle.dsl.common.runs.ide.extensions.IdeaRunExtension;
import net.neoforged.gradle.dsl.common.runs.run.Runs;
import net.neoforged.gradle.dsl.common.util.CommonRuntimeUtils;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.jetbrains.gradle.ext.*;

/**
 * A simple manager which configures runs based on the IDE it is attached to.
 */
public class IdeRunIntegrationManager {

    private static final IdeRunIntegrationManager INSTANCE = new IdeRunIntegrationManager();

    public static IdeRunIntegrationManager getInstance() {
        return INSTANCE;
    }

    private IdeRunIntegrationManager() {
    }

    /**
     * Configures the IDE integration to run runs as tasks from the IDE.
     *
     * @param project The project to configure.
     */
    public void apply(final Project project) {
        project.afterEvaluate(evaluatedProject -> {
            final IdeManagementExtension ideManager = evaluatedProject.getExtensions().getByType(IdeManagementExtension.class);
            ideManager.apply(new RunsImportAction());
        });

        final IdeManagementExtension ideManager = project.getExtensions().getByType(IdeManagementExtension.class);
        ideManager.apply(new RegisterIdeRunExtensions());
    }

    private static final class RegisterIdeRunExtensions implements IdeManagementExtension.IdeImportAction {

        @Override
        public void idea(Project project, IdeaModel idea, ProjectSettings ideaExtension) {
            final Runs runs = project.getExtensions().getByType(Runs.class);
            runs.configureEach(run -> {
                final IdeaRunExtensionImpl impls = project.getObjects().newInstance(IdeaRunExtensionImpl.class, project, run);
                run.getExtensions().add(IdeaRunExtension.class, "idea", impls);
            });
        }

        @Override
        public void eclipse(Project project, EclipseModel eclipse) {
            //TODO:
            // There is for now no native API, or library, yet which allows generating native
            // launch-files for eclipse without having to resort to unspecified launch arguments
            // when one becomes available we should implement that asap.
        }
    }

    private static final class RunsImportAction implements IdeManagementExtension.IdeImportAction {

        @Override
        public void idea(Project project, IdeaModel idea, ProjectSettings ideaExtension) {
            final RunConfigurationContainer ideaRuns = ((ExtensionAware) ideaExtension).getExtensions().getByType(RunConfigurationContainer.class);
            final Runs runs = project.getExtensions().getByType(Runs.class);

            runs.getAsMap().forEach((name, run) -> {
                final String runName = StringUtils.capitalize(project.getName() + ": " + name);

                final RunImpl runImpl = (RunImpl) run;
                final IdeaRunExtension runIdeaConfig = run.getExtensions().getByType(IdeaRunExtension.class);

                final TaskProvider<?> ideBeforeRunTask;
                if (runImpl.getTaskDependencies().size() > 0) {
                    ideBeforeRunTask = project.getTasks().register(CommonRuntimeUtils.buildTaskName("ideBeforeRun", name));
                    ideBeforeRunTask.configure(task -> {
                        runImpl.getTaskDependencies().forEach(dep -> {
                            //noinspection Convert2MethodRef Creates a compiler error regarding incompatible types.
                            task.dependsOn(dep);
                        });
                    });
                } else {
                    ideBeforeRunTask = null;
                }

                ideaRuns.register(runName, Application.class, ideaRun -> {
                    ideaRun.setMainClass(runImpl.getMainClass().get());
                    ideaRun.setWorkingDirectory(runImpl.getWorkingDirectory().get().getAsFile().getAbsolutePath());
                    ideaRun.setJvmArgs(String.join(" ", runImpl.realiseJvmArguments()));
                    ideaRun.moduleRef(project, runIdeaConfig.getPrimarySourceSet().get());
                    ideaRun.setProgramParameters(String.join(" ", runImpl.getProgramArguments().get()));
                    ideaRun.setEnvs(runImpl.getEnvironmentVariables().get());

                    ideaRun.beforeRun(beforeRuns -> {
                        beforeRuns.create("Build", Make.class);

                        if (ideBeforeRunTask != null) {
                            beforeRuns.create("Prepare Run", GradleTask.class, gradleTask -> {
                                gradleTask.setTask(ideBeforeRunTask.get());
                            });
                        }
                    });
                });
            });
        }

        @Override
        public void eclipse(Project project, EclipseModel eclipse) {
            //TODO:
            // There is for now no native API, or library, yet which allows generating native
            // launch-files for eclipse without having to resort to unspecified launch arguments
            // when one becomes available we should implement that asap.
        }
    }
}
