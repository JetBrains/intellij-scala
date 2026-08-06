package org.jetbrains.plugins.scala.lang.resolveSemanticDb.configurations

import org.jetbrains.plugins.scala.LatestScalaVersions

object ReferenceComparisonTestConfig_Scala3_Newest extends ReferenceComparisonTestConfig(
  testClassName = "ReferenceComparisonTest_Scala3_Newest",
  testDataPathFolder = "newest",
  scalaTargetVersion = LatestScalaVersions.Scala_3_8,
)
