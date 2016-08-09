package org.jetbrains.plugins.scala
package refactoring.extractMethod

import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

/**
 * Nikolay.Tropin
 * 2014-05-20
 */
class ScalaExtractMethodCaseClass extends ScalaExtractMethodTestBase {
  override def folderPath: String = super.folderPath + "caseClass/"

  def testNoReturnSeveralOutput() = {
    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter))
    doTest(settings = TypeAnnotationSettings.noTypeAnnotationForPublic(settings))
  }

  def testReturnSeveralOutput1() = doTest()

  def testReturnSeveralOutput2() = doTest()

  def testUnitReturnSeveralOutput1() = doTest()

  def testUnitReturnSeveralOutput2() = doTest()
}
