/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
 * Copyright (C) 2022 EPAM Systems, Inc.
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

import java.io.File

import org.ossreviewtoolkit.evaluator.Evaluator
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.licenses.LicenseCategorization
import org.ossreviewtoolkit.model.licenses.LicenseCategory
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

class EvaluatorRulesFunTest : StringSpec({
    "rules.kts can be compiled and executed" {
        val resultFile = getAssetFile("semver4j-analyzer-result.yml")

        val licenseClassifications = LicenseClassifications(
            categories = listOf(LicenseCategory("copyleft-limited")),
            categorizations = listOf(
                LicenseCategorization(
                    id = SpdxSingleLicenseExpression.parse("EPL-1.0"),
                    categories = setOf("copyleft-limited")
                )
            )
        )
        val ortResult = resultFile.readValue<OrtResult>()
        val evaluator = Evaluator(
            ortResult = ortResult,
            licenseClassifications = licenseClassifications
        )

        val script = getAssetFile("rules.kts").readText()

        val result = evaluator.run(script)

        result.violations.map { it.rule }.distinct() shouldContainExactlyInAnyOrder listOf(
            "COPYLEFT_LIMITED_IN_SOURCE",
            "DEPRECATED_SCOPE_EXCLUDE_REASON_IN_ORT_YML",
            "HIGH_SEVERITY_VULNERABILITY_IN_PACKAGE",
            "MISSING_CONTRIBUTING_FILE",
            "MISSING_README_FILE_LICENSE_SECTION",
            "UNHANDLED_LICENSE",
            "VULNERABILITY_IN_PACKAGE"
        )
    }
})

private fun getAssetFile(path: String) = File("src/funTest/assets").resolve(path)
