package org.jetbrains.plugins.scala
package refactoring.extractMethod

import org.jetbrains.plugins.scala.lang.refactoring.util.TypeAnnotationSettings

/**
 * Nikolay.Tropin
 * 2014-05-20
 */
class ScalaExtractMethodCaseClass extends ScalaExtractMethodTestBase {
  override def folderPath: String = super.folderPath + "caseClass/"

  def testNoReturnSeveralOutput() = {
    TypeAnnotationSettings.alwaysAddType(getProjectAdapter)
    TypeAnnotationSettings.noTypeAnnotationForPublic(getProjectAdapter)

    doTest(specifyReturnType = false)
  }

  def testReturnSeveralOutput1() = doTest()

  def testReturnSeveralOutput2() = doTest()

  def testUnitReturnSeveralOutput1() = doTest()

  def testUnitReturnSeveralOutput2() = doTest()
}
