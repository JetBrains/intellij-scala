package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class TypeInferenceCanBuildFromTest extends TypeInferenceTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
  override protected def folderPath: Path = super.folderPath / "bugs3"

  def testSCL2495A(): Unit = doTest()
}
