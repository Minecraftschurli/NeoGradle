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

import net.neoforged.gradle.dsl.common.util.Artifact;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;

@CacheableTask
public abstract class DownloadMavenArtifact extends DownloadingTask {
    private final Property<Artifact> artifact;
    private final Property<Boolean> changing;

    public DownloadMavenArtifact() {
        // We need to always ask, in case the file on the remote maven/local fake repo has changed.
        getOutputs().upToDateWhen(task -> false);

        this.artifact = getProject().getObjects().property(Artifact.class);
        this.changing = getProject().getObjects().property(Boolean.class);

        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName())
                        .zip(getArtifact(), (d, a) -> d.file("output." + a.getExtension())));
    }

    @Internal
    public Provider<String> getResolvedVersion() {
        return getArtifact().flatMap(a -> getDownloader().flatMap(d -> d.version(a.getDescriptor())));
    }

    @Input
    public Property<Artifact> getArtifact() {
        return this.artifact;
    }

    public void setArtifact(String value) {
        getArtifact().set(Artifact.from(value));
    }

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    public void run() throws IOException {
        final Provider<File> downloadedArtifact = getDownloader().flatMap(d -> getArtifact().flatMap(a -> d.file(a.getDescriptor())));
        setDidWork(downloadedArtifact.isPresent() && downloadedArtifact.get().exists());

        File output = getOutput().get().getAsFile();
        if (FileUtils.contentEquals(downloadedArtifact.get(), output)) return;
        if (output.exists()) output.delete();
        if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
        FileUtils.copyFile(downloadedArtifact.get(), output);
    }
}
