package net.neoforged.gradle.userdev

import net.neoforged.trainingwheels.gradle.functional.SimpleTestSpecification
import org.gradle.testkit.runner.TaskOutcome

class FunctionalTests extends SimpleTestSpecification {

    protected File codeFile

    @Override
    def setup() {
        codeFile = new File(testProjectDir, 'src/main/java/net/minecraftforge/gradle/userdev/FunctionalTests.java')
        codeFile.getParentFile().mkdirs()
    }

    def "a mod with userdev as dependency can run the patch task for that dependency"() {
        given:
        settingsFile << "rootProject.name = 'test-project'"
        buildFile << """
            plugins {
                id 'net.neoforged.gradle.userdev'
            }
            
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }
            
            dependencies {
                implementation 'net.minecraftforge:forge:+'
            }
        """

        when:
        def result = gradleRunner()
                .withArguments('--stacktrace', 'neoFormRecompile')
                .build()

        then:
        result.output.contains('BUILD SUCCESSFUL')
    }

    def "a mod with userdev as dependency and official mappings can compile through gradle"() {
        given:
        settingsFile << "rootProject.name = 'mcp-plugin-apply-succeeds'"
        buildFile << """
            plugins {
                id 'net.neoforged.gradle.userdev'
            }
            
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }
            
            dependencies {
                implementation 'net.minecraftforge:forge:+'
            }
        """
        codeFile << """
            package net.neoforged.gradle.mcp;
            
            import net.minecraft.client.Minecraft;
            
            public class FunctionalTests {
                public static void main(String[] args) {
                    System.out.println(Minecraft.getInstance().getClass().toString());
                }
            }
        """

        when:
        def result = runTask('build')

        then:
        result.output.contains('BUILD SUCCESSFUL')
    }

    def "the userdev runtime by default supports the build cache"() {
        given:
        settingsFile << "rootProject.name = 'mcp-plugin-apply-succeeds'"
        buildFile << """
            plugins {
                id 'net.neoforged.gradle.userdev'
            }
            
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(17)
                }
            }
            
            dependencies {
                implementation 'net.minecraftforge:forge:+'
            }
        """
        codeFile << """
            package net.neoforged.gradle.mcp;
            
            import net.minecraft.client.Minecraft;
            
            public class FunctionalTests {
                public static void main(String[] args) {
                    System.out.println(Minecraft.getInstance().getClass().toString());
                }
            }
        """

        when:
        def result = runTask('--build-cache', 'build')

        then:
        result.task(":neoFormRecompile").outcome == TaskOutcome.SUCCESS

        when:
        new File(testProjectDir, 'build').deleteDir()
        result = runTask('--build-cache', 'build')

        then:
        result.task(":neoFormRecompile").outcome == TaskOutcome.FROM_CACHE
    }
}
