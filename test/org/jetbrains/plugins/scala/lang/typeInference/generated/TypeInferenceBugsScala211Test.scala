package org.jetbrains.plugins.scala.lang.typeInference.generated

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 07.12.15.
  */
class TypeInferenceBugsScala211Test extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs211/"

  override protected def getDefaultScalaSDKVersion: ScalaSdkVersion = ScalaSdkVersion._2_11

  def testSCL9429(): Unit = doTest()
}
