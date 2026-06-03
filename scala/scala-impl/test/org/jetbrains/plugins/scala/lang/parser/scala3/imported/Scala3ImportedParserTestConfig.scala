package org.jetbrains.plugins.scala.lang.parser.scala3.imported

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.resolveSemanticDb.configurations.{ReferenceComparisonTestConfig_Scala3_LTS, ReferenceComparisonTestConfig_Scala3_Newest}

abstract class Scala3ImportedParserTestConfig(val testDataPathFolder: String, val scalaTargetVersion: ScalaVersion) {

  val successDataDirectory = s"parser/scala3Import/$testDataPathFolder/success"
  val failDataDirectory = s"parser/scala3Import/$testDataPathFolder/fail"
  val rangesDirectory = s"parser/scala3Import/$testDataPathFolder/ranges"
}

object Scala3ImportedParserTestConfig {
  object LTS extends Scala3ImportedParserTestConfig(
    testDataPathFolder = "lts",
    scalaTargetVersion = ReferenceComparisonTestConfig_Scala3_LTS.scalaTargetVersion
  )
  object Newest extends Scala3ImportedParserTestConfig(
    testDataPathFolder = "newest",
    scalaTargetVersion = ReferenceComparisonTestConfig_Scala3_Newest.scalaTargetVersion
  )
}


