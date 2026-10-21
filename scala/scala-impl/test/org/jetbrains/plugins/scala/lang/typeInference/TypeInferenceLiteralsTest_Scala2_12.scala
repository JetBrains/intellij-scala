package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.lang.typeInference.generated.TypeInferenceLiteralsTest

class TypeInferenceLiteralsTest_Scala2_12 extends TypeInferenceLiteralsTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}
