/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.neoforged.gradle.common.tasks;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.codehaus.groovy.control.io.NullWriter;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Executes the tool JAR.
 *
 * <p>The tool JAR is specified using Maven coordinates, and downloaded using the repositories defined through Gradle.</p>
 */
// TODO: refactor to extend JavaExec?
@CacheableTask
public abstract class JarExec extends DownloadingTask {
    protected boolean hasLog = true;

    private final Provider<File> toolFile;
    private final Provider<String> resolvedVersion;

    protected final Provider<Directory> workDir = getProject().getLayout().getBuildDirectory().dir(getName());
    protected final Provider<RegularFile> logFile = workDir.map(d -> d.file("log.txt"));

    public JarExec() {
        toolFile = getTool().flatMap(toolStr -> getDownloader().flatMap(downloader -> downloader.file(toolStr)));
        resolvedVersion = getTool().flatMap(toolStr -> getDownloader().flatMap(downloader -> downloader.version(toolStr)));

        getDebug().convention(false);

        final JavaPluginExtension extension = getProject().getExtensions().findByType(JavaPluginExtension.class);
        if (extension != null) {
            getJavaLauncher().convention(getJavaToolchainService().launcherFor(extension.getToolchain()));
        }
    }

    @Inject
    protected JavaToolchainService getJavaToolchainService() {
        throw new UnsupportedOperationException("Decorated instance, this should never be thrown unless shenanigans");
    }

    @TaskAction
    public void apply() throws IOException {
        File jar = getToolJar().get();
        File logFile = this.logFile.get().getAsFile();

        // Locate main class in jar file
        JarFile jarFile = new JarFile(jar);
        String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        jarFile.close();

        // Create parent directory for log file
        Logger logger = getProject().getLogger();
        if (logFile.getParentFile() != null && !logFile.getParentFile().exists() && !logFile.getParentFile().mkdirs()) {
            logger.warn("Could not create parent directory '{}' for log file", logFile.getParentFile().getAbsolutePath());
        }

        final boolean debug = getDebug().get();
        final List<String> args = filterArgs(getArgs().get());
        final ConfigurableFileCollection classpath = getProject().files(getToolJar(), getClasspath());
        final File workingDirectory = workDir.get().getAsFile();

        try (PrintWriter log = new PrintWriter(hasLog ? new FileWriter(logFile) : NullWriter.DEFAULT, true)) {
            getProject().javaexec(spec -> {
                spec.setExecutable(getEffectiveExecutable());
                spec.setDebug(debug);
                spec.setArgs(args);
                spec.setClasspath(classpath);
                spec.setWorkingDir(workingDirectory);
                spec.getMainClass().set(mainClass);

                log.println("Java Launcher: " + spec.getExecutable());
                log.println("Arguments: " + args.stream().collect(Collectors.joining(", ", "'", "'")));
                log.println("Classpath:");
                classpath.forEach(f -> log.println(" - " + f.getAbsolutePath()));
                log.println("Working directory: " + workingDirectory.getAbsolutePath());
                log.println("Main class: " + mainClass);
                log.println("====================================");

                spec.setStandardOutput(new OutputStream() {
                    @Override
                    public void flush() { log.flush(); }
                    @Override
                    public void close() {}
                    @Override
                    public void write(int b) { log.write(b); }
                });
            }).rethrowFailure().assertNormalExitValue();
        }

        if (hasLog) {
            postProcess(logFile);
        }

        // Delete working directory if empty
        final String[] workingDirContents = workingDirectory.list();
        if ((workingDirContents == null || workingDirContents.length == 0) && !workingDirectory.delete()) {
            logger.warn("Could not delete empty working directory '{}'", workingDirectory.getAbsolutePath());
        }
    }

    protected List<String> filterArgs(List<String> args) {
        return args;
    }

    // TODO: remove this? as this isn't used anywhere
    protected void postProcess(File log) {
    }

    // Should be used only if it can be guaranteed that there is at least one value for each key in the multiPrefixedReplacements
    // Otherwise, use replaceArgs to ensure keys without any linked values still exist
    protected List<String> replaceArgsMulti(List<String> args,
                                       @Nullable Map<String, ?> normalReplacements,
                                       @Nullable Multimap<String, ?> multiPrefixedReplacements) {
        multiPrefixedReplacements = multiPrefixedReplacements != null ? multiPrefixedReplacements : ImmutableMultimap.of();
        return replaceArgs(args, normalReplacements, multiPrefixedReplacements.asMap());
    }

    protected List<String> replaceArgs(List<String> args,
                                       @Nullable Map<String, ?> normalReplacements,
                                       @Nullable Map<String, ? extends Collection<?>> multiPrefixedReplacements) {
        // prevent nulls
        normalReplacements = normalReplacements != null ? normalReplacements : Collections.emptyMap();
        multiPrefixedReplacements = multiPrefixedReplacements != null ? multiPrefixedReplacements : Collections.emptyMap();
        if (normalReplacements.isEmpty() && multiPrefixedReplacements.isEmpty()) return args;

        ArrayList<String> newArgs = new ArrayList<>(args.size());

        // normalReplacements, it is a normal token substitution
        // multiPrefixedReplacements, it will take the previous token and prepend that to each value for the token

        for (String arg : args) {
            if (multiPrefixedReplacements.containsKey(arg)) {
                String prefix = newArgs.isEmpty() ? null : newArgs.remove(newArgs.size() - 1);
                for (Object value : multiPrefixedReplacements.get(arg)) {
                    if (prefix != null) newArgs.add(prefix);
                    newArgs.add(toString(value));
                }
            } else if (normalReplacements.containsKey(arg)) {
                newArgs.add(toString(normalReplacements.get(arg)));
            } else {
                newArgs.add(arg);
            }
        }

        return newArgs;
    }

    private String toString(Object obj) {
        if (obj instanceof File) {
            return ((File) obj).getAbsolutePath();
        } else if (obj instanceof Path) {
            return ((Path) obj).toAbsolutePath().toString();
        }
        return Objects.toString(obj);
    }

    @Internal
    public String getResolvedVersion() {
        return resolvedVersion.get();
    }

    @Input
    public boolean getHasLog() {
        return hasLog;
    }

    public void setHasLog(boolean value) {
        this.hasLog = value;
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public Provider<File> getToolJar() {
        return toolFile;
    }

    @Input
    public abstract Property<String> getTool();

    @Input
    public abstract ListProperty<String> getArgs();

    @Input
    @Optional
    public abstract Property<Boolean> getDebug();

    @Optional
    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @Nested
    @Optional
    public abstract Property<JavaLauncher> getJavaLauncher();

    public void setMinimumRuntimeJavaVersion(int version) {
        if (!getJavaLauncher().isPresent() || !getJavaLauncher().get().getMetadata().getLanguageVersion().canCompileOrRun(version)) {
            setRuntimeJavaVersion(version);
        }
    }

    public void setRuntimeJavaVersion(int version) {
        setRuntimeJavaToolchain(tc -> tc.getLanguageVersion().set(JavaLanguageVersion.of(version)));
    }

    public void setRuntimeJavaToolchain(JavaToolchainSpec toolchain) {
        getJavaLauncher().set(getJavaToolchainService().launcherFor(toolchain));
    }

    public void setRuntimeJavaToolchain(Action<? super JavaToolchainSpec> action) {
        getJavaLauncher().set(getJavaToolchainService().launcherFor(action));
    }

    private String getEffectiveExecutable() {
        if (getJavaLauncher().isPresent()) {
            return getJavaLauncher().get().getExecutablePath().toString();
        } else {
            return Jvm.current().getJavaExecutable().getAbsolutePath();
        }
    }
}
