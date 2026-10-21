package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

abstract class PartialFunctionSynthesisTest extends TypeInferenceTestBase {
  override protected def folderPath: Path = super.folderPath / "bugs5"

  def testSCL6716(): Unit = doTest()
  def testSCL9094(): Unit = doTest()
}

class PartialFunctionSynthesisTest_from_Scala2_13 extends PartialFunctionSynthesisTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version > LatestScalaVersions.Scala_2_12
}


class PartialFunctionSynthesisTest_before_Scala2_13 extends PartialFunctionSynthesisTest {
  override protected def supportedIn(version: ScalaVersion): Boolean = version <= LatestScalaVersions.Scala_2_12
  override protected def shouldPass: Boolean = false
}
