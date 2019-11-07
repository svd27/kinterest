/*
 * This file was generated by the Gradle 'init' task.
 *
 * The settings file is used to specify which projects to include in your build.
 *
 * Detailed information about configuring a multi-project build in Gradle can be found
 * in the user manual at https://docs.gradle.org/5.5.1/userguide/multi_project_builds.html
 */

rootProject.name = "kInterest"

include("core", "core:annotations", "core:generator", "core:common", "core:jvm-backend")
include("datastores", "datastores:mongo", "datastores:hazelcast", "datastores:tests")
include("docker", "docker:docker-client", "docker:testcontainers")


val kotlinVersion: String by settings

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jetbrains.kotlin.multiplatform" -> {
                    useVersion(kotlinVersion)
                }
                "org.jetbrains.kotlin.kapt" -> {
                    useVersion(kotlinVersion)
                }
                "org.jetbrains.kotlin.jvm" -> {
                    useVersion(kotlinVersion)
                }
                "org.jetbrains.kotlin.js" -> {
                    useVersion(kotlinVersion)
                }
            }
        }
    }
}
