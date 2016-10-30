package org.jetbrains.plugins.scala.lang.typeInference.generated

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author Nikolay.Tropin
  */
class TypeInferenceCatsTest extends TypeInferenceTestBase {
  override protected def additionalLibraries(): Array[String] = Array("cats")

  override protected def folderPath: String = super.folderPath + "cats/"

  def testSCL10006() = doTest()

//  TODO: this test actually passes in debug IDEA, but failes in tests (ReferenceExpressionResolver.resolve() succeeds
//   in debug idea with the same dependencies, while in tests it returns resolve failure)
//  def testSCL10237() = doTest()

  def testSCL10237_1() = doTest()

  def testSCL10237_2() = doTest()
}
