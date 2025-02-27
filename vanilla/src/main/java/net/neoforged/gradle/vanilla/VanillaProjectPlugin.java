package net.neoforged.gradle.vanilla;

import net.neoforged.gradle.common.CommonPlugin;
import net.neoforged.gradle.util.UrlConstants;
import net.neoforged.gradle.vanilla.dependency.VanillaDependencyManager;
import net.neoforged.gradle.vanilla.runtime.extensions.VanillaRuntimeExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class VanillaProjectPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPlugins().apply(CommonPlugin.class);

        VanillaRuntimeExtension runtimeExtension = project.getExtensions().create("vanillaRuntimes", VanillaRuntimeExtension.class, project);

        //Setup handling of the dependencies
        VanillaDependencyManager.getInstance().apply(project);

        //Add Known repos, -> The default tools come from this repo.
        project.getRepositories().maven(e -> {
            e.setUrl(UrlConstants.NEO_FORGE_MAVEN);
            e.metadataSources(m -> {
                m.mavenPom();
                m.artifact();
            });
        });

        project.getRepositories().maven(e -> {
            e.setUrl(UrlConstants.MCF_FORGE_MAVEN);
            e.metadataSources(m -> {
                m.mavenPom();
                m.artifact();
            });
        });

    }
}
