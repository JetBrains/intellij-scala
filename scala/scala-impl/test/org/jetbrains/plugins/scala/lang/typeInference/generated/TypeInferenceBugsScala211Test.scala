package org.jetbrains.plugins.scala
package lang
package typeInference
package generated

import org.jetbrains.plugins.scala.extensions.PathExt

import java.nio.file.Path

class TypeInferenceBugsScala211Test extends TypeInferenceTestBase {
  override def folderPath: Path = super.folderPath / "bugs211"

  override protected def supportedIn(version: ScalaVersion): Boolean = version  == LatestScalaVersions.Scala_2_11

  def testSCL9429(): Unit = doTest()
}
