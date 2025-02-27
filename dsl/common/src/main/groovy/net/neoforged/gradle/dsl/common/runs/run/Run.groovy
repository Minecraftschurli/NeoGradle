package net.neoforged.gradle.dsl.common.runs.run


import groovy.transform.CompileStatic;
import groovyjarjarantlr4.v4.runtime.misc.NotNull
import net.minecraftforge.gdi.BaseDSLElement
import net.minecraftforge.gdi.NamedDSLElement;
import net.minecraftforge.gdi.annotations.DSLProperty
import net.neoforged.gradle.dsl.common.runs.type.Type
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider

/**
 * Defines an object which represents a single configuration for running the game.
 * Gradle tasks, IDE run configurations, and other run configurations are all created from this object.
 */
@CompileStatic
interface Run extends BaseDSLElement<Run>, NamedDSLElement {


    /**
     * Defines the environment variables that are passed to the JVM when running the game.
     *
     * @return The environment variables that are passed to the JVM when running the game.
     */
    @Input
    @DSLProperty
    MapProperty<String, String> getEnvironmentVariables();

    /**
     * Defines the main class that is executed when the game is started.
     *
     * @return The main class that is executed when the game is started.
     */
    @Input
    @DSLProperty
    Property<String> getMainClass();

    /**
     * Indicates if all the projects in the current Gradle project should be build ahead of running the game.
     *
     * @return {@code true} if all the projects in the current Gradle project should be build ahead of running the game; otherwise, {@code false}.
     */
    @Input
    @DSLProperty
    Property<Boolean> getShouldBuildAllProjects();

    /**
     * Defines the program arguments that are passed to the application when running the game.
     *
     * @return The program arguments that are passed to the application when running the game.
     */
    @Input
    @DSLProperty
    ListProperty<String> getProgramArguments();

    /**
     * Defines the JVM arguments that are passed to the JVM when running the game.
     *
     * @return The JVM arguments that are passed to the JVM when running the game.
     */
    @Input
    @DSLProperty
    ListProperty<String> getJvmArguments();

    /**
     * Indicates if this run is a single instance run.
     * If this is set to true, then no other copy of this run configuration can be started while this run configuration is running.
     *
     * @return {@code true} if this run is a single instance run; otherwise, {@code false}.
     */
    @Input
    @DSLProperty
    Property<Boolean> getIsSingleInstance();

    /**
     * Defines the system properties that are passed to the JVM when running the game.
     *
     * @return The system properties that are passed to the JVM when running the game.
     */
    @Input
    @DSLProperty
    MapProperty<String, String> getSystemProperties();

    /**
     * Defines the working directory that is used when running the game.
     *
     * @return The working directory that is used when running the game.
     */
    @OutputDirectory
    @DSLProperty
    DirectoryProperty getWorkingDirectory();

    /**
     * Indicates if this run is a client run.
     *
     * @return {@code true} if this run is a client run; otherwise, {@code false}.
     */
    @Input
    @DSLProperty
    Property<Boolean> getIsClient();

    /**
     * Defines the source sets that are used as a mod.
     *
     * @return The source sets that are used as a mod.
     */
    @Internal
    @DSLProperty
    ListProperty<SourceSet> getModSources();

    /**
     * Gives access to the classpath for this run.
     * Does not contain the full classpath since that is dependent on the actual run environment, but contains the additional classpath elements
     * needed to run the game with this run.
     *
     * @return The property which holds the classpath.
     */
    @InputFiles
    @Classpath
    @DSLProperty
    ConfigurableFileCollection getClasspath();

    /**
     * Defines the custom dependency handler for each run.
     *
     * @return The dependency handler for the run.
     */
    @Nested
    @DSLProperty
    Property<DependencyHandler> getDependencies();

    /**
     * Indicates if this run should automatically be configured.
     *
     * @return The property which indicates if this run should automatically be configured.
     */
    @Input
    @DSLProperty
    @Optional
    Property<Boolean> getConfigureAutomatically();

    /**
     * Indicates if this run should automatically be configured by the type of the same name.
     *
     * @return The property which indicates if this run should automatically be configured by the type of the same name.
     */
    @Input
    @DSLProperty
    @Optional
    Property<Boolean> getConfigureFromTypeWithName();

    /**
     * Indicates if this run should automatically be configured by its dependent runtimes.
     *
     * @return The property which indicates if this run should automatically be configured by its dependent runtimes.
     */
    @Input
    @DSLProperty
    @Optional
    Property<Boolean> getConfigureFromDependencies();


    /**
     * Configures the run using the type with the same name.
     * Throwing an exception if no type could be found.
     */
    void configure();

    /**
     * Configures the run using the type with the specified name.
     * Throwing an exception if no type could be found.
     *
     * @param type The name of the type to use to configure the run.
     */
    void configure(@NotNull final String type);

    /**
     * Configures the run using the given type.
     *
     * @param type The type to use to configure the run.
     */
    void configure(@NotNull final Type type);

    /**
     * Configures the run to execute the given tasks before running the run.
     *
     * @param tasks The tasks to depend on.
     */
    void dependsOn(@NotNull final TaskProvider<? extends Task>... tasks);
}
