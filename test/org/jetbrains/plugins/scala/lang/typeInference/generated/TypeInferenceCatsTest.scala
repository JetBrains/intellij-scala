package org.jetbrains.plugins.scala.lang.typeInference.generated

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author Nikolay.Tropin
  */
class TypeInferenceCatsTest extends TypeInferenceTestBase {
  override protected def additionalLibraries(): Array[String] = Array("cats")

  override protected def folderPath: String = super.folderPath + "cats/"

  def testSCL10006() = doTest()
}
