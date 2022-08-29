package org.jetbrains.plugins.scala
package refactoring.extractTrait

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.refactoring.extractTrait.ScalaExtractTraitHandler

import scala.annotation.nowarn

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
abstract class ExtractTraitTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  def checkResult(fileText: String, expectedText: String, onlyDeclarations: Boolean, onlyFirstMember: Boolean = false): Unit = {
    configureFromFileText("dummy.scala", fileText.replace("\r", "").stripMargin.trim)
    implicit val project: Project = getProject
    implicit val editor: Editor = getEditor
    new ScalaExtractTraitHandler().testInvoke(getFile, onlyDeclarations, onlyFirstMember)
    UsefulTestCase.doPostponedFormatting(project)
    checkResultByText(expectedText.replace("\r", "").stripMargin.trim)
  }

  def checkException(fileText: String, messageText: String, onlyDeclarations: Boolean, onlyFirstMember: Boolean): Unit = {
    configureFromFileText("dummy.scala", fileText.replace("\r", "").stripMargin.trim)
    try {
      implicit val project: Project = getProject
      implicit val editor: Editor = getEditor
      new ScalaExtractTraitHandler().testInvoke(getFile, onlyDeclarations, onlyFirstMember)
      assert(assertion = false, "Exception was not thrown")
    } catch {
      case e: Exception => assert(messageText == e.getMessage)
    }
  }
}
