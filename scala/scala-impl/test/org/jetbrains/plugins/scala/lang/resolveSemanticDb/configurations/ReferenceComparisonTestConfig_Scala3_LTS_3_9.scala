package org.jetbrains.plugins.scala.lang.resolveSemanticDb.configurations

import org.jetbrains.plugins.scala.LatestScalaVersions

object ReferenceComparisonTestConfig_Scala3_LTS_3_9 extends ReferenceComparisonTestConfig(
  testClassName = "ReferenceComparisonTest_Scala3_LTS_3_9",
  testDataPathFolder = "lts39",
  scalaTargetVersion = LatestScalaVersions.Scala_3_9,
)
