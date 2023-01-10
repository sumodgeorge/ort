/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

import java.nio.charset.Charset
import java.nio.file.Files

import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask

@Suppress("DSL_SCOPE_VIOLATION") // See https://youtrack.jetbrains.com/issue/KTIJ-19369.
plugins {
    // Apply core plugins.
    application

    // Apply third-party plugins.
    alias(libs.plugins.graalVmNativeImage)
}

application {
    applicationName = "ort"
    mainClass.set("org.ossreviewtoolkit.cli.OrtMainKt")
}

graalvmNative {
    // For options see https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html.
    binaries {
        named("main") {
            imageName.set("ort")

            buildArgs.add(
                listOf(
                    "ch.qos.logback.classic.Level",
                    "ch.qos.logback.classic.Logger",
                    "ch.qos.logback.classic.PatternLayout",
                    "ch.qos.logback.core.CoreConstants",
                    "ch.qos.logback.core.status.InfoStatus",
                    "ch.qos.logback.core.status.StatusBase",
                    "ch.qos.logback.core.util.Loader",
                    "ch.qos.logback.core.util.StatusPrinter",
                    "org.slf4j.LoggerFactory"
                ).joinToString(",", prefix = "--initialize-at-build-time=")
            )
        }
    }

    metadataRepository {
        enabled.set(true)
    }
}

tasks.named<BuildNativeImageTask>("nativeCompile").configure {
    val toolchainDir = options.get().javaLauncher.get().executablePath.asFile.parentFile.run {
        if (name == "bin") parentFile else this
    }

    val toolchainFiles = toolchainDir.walkTopDown().filter { it.isFile }
    val knownEmptyFiles = setOf(".registry", "provisioned.ok")
    val emptyFiles = toolchainFiles.filter { it.length() == 0L && it.name !in knownEmptyFiles }

    val links = toolchainFiles.mapNotNull { file ->
        emptyFiles.singleOrNull { it != file && it.name == file.name }?.let {
            file to it
        }
    }

    // Fix up symbolic links that Gradle's Copy-task cannot handle, see https://github.com/gradle/gradle/issues/3982.
    links.forEach { (target, link) ->
        logger.quiet("Fixing up '$link' to link to '$target'.")

        if (link.delete()) {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } else {
            logger.warn("Unable to delete '$link'.")
        }
    }
}

tasks.named<CreateStartScripts>("startScripts").configure {
    doLast {
        // Work around the command line length limit on Windows when passing the classpath to Java, see
        // https://github.com/gradle/gradle/issues/1989#issuecomment-395001392.
        val windowsScriptText = windowsScript.readText(Charset.defaultCharset())
        windowsScript.writeText(
            windowsScriptText.replace(
                Regex("set CLASSPATH=%APP_HOME%\\\\lib\\\\.*"),
                "set CLASSPATH=%APP_HOME%\\\\lib\\\\*;%APP_HOME%\\\\plugin\\\\*"
            )
        )

        val unixScriptText = unixScript.readText(Charset.defaultCharset())
        unixScript.writeText(
            unixScriptText.replace(
                Regex("CLASSPATH=\\\$APP_HOME/lib/.*"),
                "CLASSPATH=\\\$APP_HOME/lib/*:\\\$APP_HOME/plugin/*"
            )
        )
    }
}

repositories {
    // Need to repeat several custom repository definitions of other submodules here, see
    // https://github.com/gradle/gradle/issues/4106.
    exclusiveContent {
        forRepository {
            maven("https://repo.gradle.org/gradle/libs-releases/")
        }

        filter {
            includeGroup("org.gradle")
        }
    }

    exclusiveContent {
        forRepository {
            maven("https://repo.eclipse.org/content/repositories/sw360-releases/")
        }

        filter {
            includeGroup("org.eclipse.sw360")
        }
    }

    exclusiveContent {
        forRepository {
            maven("https://jitpack.io/")
        }

        filter {
            includeGroup("com.github.ralfstuckert.pdfbox-layout")
            includeGroup("com.github.everit-org.json-schema")
        }
    }
    exclusiveContent {
        forRepository {
            maven("https://packages.atlassian.com/maven-external")
        }

        filter {
            includeGroupByRegex("com\\.atlassian\\..*")
            includeVersionByRegex("log4j", "log4j", ".*-atlassian-.*")
        }
    }
}

dependencies {
    implementation(project(":advisor"))
    implementation(project(":analyzer"))
    implementation(project(":downloader"))
    implementation(project(":evaluator"))
    implementation(project(":model"))
    implementation(project(":notifier"))
    implementation(project(":reporter"))
    implementation(project(":scanner"))
    implementation(project(":utils:ort-utils"))
    implementation(project(":utils:spdx-utils"))

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.bundles.exposed)
    implementation(libs.clikt)
    implementation(libs.hikari)
    implementation(libs.jacksonModuleKotlin)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kotlinxSerialization)
    implementation(libs.log4jApiToSlf4j)
    implementation(libs.logbackClassic)
    implementation(libs.postgres)
    implementation(libs.reflections)
    implementation(libs.sw360Client)

    testImplementation(project(":utils:test-utils"))

    testImplementation(libs.greenmail)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(libs.kotestRunnerJunit5)

    funTestImplementation(sourceSets["main"].output)
    funTestImplementation(sourceSets["test"].output)
}

configurations["funTestImplementation"].extendsFrom(configurations["testImplementation"])
