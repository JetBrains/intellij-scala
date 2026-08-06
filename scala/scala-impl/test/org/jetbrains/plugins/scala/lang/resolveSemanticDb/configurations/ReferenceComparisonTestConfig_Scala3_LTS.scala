package org.jetbrains.plugins.scala.lang.resolveSemanticDb.configurations

import org.jetbrains.plugins.scala.LatestScalaVersions

object ReferenceComparisonTestConfig_Scala3_LTS extends ReferenceComparisonTestConfig(
  testClassName = "ReferenceComparisonTest_Scala3_LTS",
  testDataPathFolder = "lts",
  scalaTargetVersion = LatestScalaVersions.Scala_3_LTS,
)
