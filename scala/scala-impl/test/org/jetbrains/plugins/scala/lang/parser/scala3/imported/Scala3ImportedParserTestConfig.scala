package org.jetbrains.plugins.scala.lang.parser.scala3.imported

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.configurations.{ReferenceComparisonTestConfig_Scala3_LTS, ReferenceComparisonTestConfig_Scala3_LTS_3_9, ReferenceComparisonTestConfig_Scala3_Newest}

abstract class Scala3ImportedParserTestConfig(
  val testDataPathFolder: String,
  val scalaTargetVersion: ScalaVersion,
  val extraFilesInFailedIgnore: Set[String]
) {

  val successDataDirectory = s"parser/scala3Import/$testDataPathFolder/success"
  val failDataDirectory = s"parser/scala3Import/$testDataPathFolder/fail"
  val rangesDirectory = s"parser/scala3Import/$testDataPathFolder/ranges"
}

object Scala3ImportedParserTestConfig {
  object LTS extends Scala3ImportedParserTestConfig(
    testDataPathFolder = "lts",
    scalaTargetVersion = ReferenceComparisonTestConfig_Scala3_LTS.scalaTargetVersion,
    extraFilesInFailedIgnore = Set.empty
  )
  object Newest extends Scala3ImportedParserTestConfig(
    testDataPathFolder = "newest",
    scalaTargetVersion = ReferenceComparisonTestConfig_Scala3_Newest.scalaTargetVersion,
    extraFilesInFailedIgnore = Set(
      "alphanumeric-infix-operator-compat_D_1_c3_3_0.test",
      "alphanumeric-infix-operator-compat_B_1_c3_1_0.test",
      "alphanumeric-infix-operator-compat_A_1_c3_0_0.test",
      "alphanumeric-infix-operator-compat_C_1_c3_2_0.test"
    )
  )
  object LTS_3_9 extends Scala3ImportedParserTestConfig(
    testDataPathFolder = "lts39",
    scalaTargetVersion = ReferenceComparisonTestConfig_Scala3_LTS_3_9.scalaTargetVersion,
    extraFilesInFailedIgnore = Set(
      "alphanumeric-infix-operator-compat_D_1_c3_3_0.test",
      "alphanumeric-infix-operator-compat_B_1_c3_1_0.test",
      "alphanumeric-infix-operator-compat_A_1_c3_0_0.test",
      "alphanumeric-infix-operator-compat_C_1_c3_2_0.test"
    )
  )
}


