/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.analyzer.managers.utils

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.Identifier

class NuGetSupportTest : WordSpec({
    "getIdentifier()" should {
        "split the namespace from the name" {
            assertSoftly {
                getIdentifier("SharpCompress", "0.23.0") shouldBe
                        Identifier("NuGet::SharpCompress:0.23.0")
                getIdentifier("System.IO", "4.1.0") shouldBe
                        Identifier("NuGet:System:IO:4.1.0")
                getIdentifier("System.IO.Compression", "4.3.0") shouldBe
                        Identifier("NuGet:System.IO:Compression:4.3.0")
                getIdentifier("System.IO.Compression.ZipFile", "4.0.1") shouldBe
                        Identifier("NuGet:System.IO:Compression.ZipFile:4.0.1")
            }
        }
    }

    "getRegistrationsBaseUrls()" should {
        "parse index urls" {
            val reader = NuGetConfigFileReader()
            val configFile = File("src/funTest/assets/projects/synthetic/nuget/nuget.config")
            val result = reader.getRegistrationsBaseUrls(configFile)

            result should containExactly(
                "https://api.nuget.org/v3/index.json",
                "https://pkgs.dev.azure.com/ms/terminal/_packaging/TerminalDependencies/nuget/v3/index.json"
            )
        }
    }
})
