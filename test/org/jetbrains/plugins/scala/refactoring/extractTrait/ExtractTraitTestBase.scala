package org.jetbrains.plugins.scala
package refactoring.extractTrait

import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.refactoring.extractTrait.ScalaExtractTraitHandler

/**
 * Nikolay.Tropin
 * 2014-06-02
 */
abstract class ExtractTraitTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  def checkResult(fileText: String, expectedText: String, onlyDeclarations: Boolean, onlyFirstMember: Boolean = false) {
    configureFromFileTextAdapter("dummy.scala", fileText.replace("\r", "").stripMargin.trim)
    new ScalaExtractTraitHandler().testInvoke(getProjectAdapter, getEditorAdapter, getFileAdapter, onlyDeclarations, onlyFirstMember)
    checkResultByText(expectedText.replace("\r", "").stripMargin.trim)
  }

  def checkException(fileText: String, messageText: String, onlyDeclarations: Boolean, onlyFirstMember: Boolean) {
    configureFromFileTextAdapter("dummy.scala", fileText.replace("\r", "").stripMargin.trim)
    try {
      new ScalaExtractTraitHandler().testInvoke(getProjectAdapter, getEditorAdapter, getFileAdapter, onlyDeclarations, onlyFirstMember)
      assert(false, "Exception was not thrown")
    } catch {
      case e: Exception => assert(messageText == e.getMessage)
    }
  }
}
