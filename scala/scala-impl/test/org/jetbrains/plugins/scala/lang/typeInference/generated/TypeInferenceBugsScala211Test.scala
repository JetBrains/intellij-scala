package org.jetbrains.plugins.scala.lang.typeInference.generated

import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_11}
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 07.12.15.
  */
class TypeInferenceBugsScala211Test extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs211/"

  override implicit val version: ScalaVersion = Scala_2_11

  def testSCL9429(): Unit = doTest()
}
