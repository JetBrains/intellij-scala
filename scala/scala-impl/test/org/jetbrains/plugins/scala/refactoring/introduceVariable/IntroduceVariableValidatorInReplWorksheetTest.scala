package org.jetbrains.plugins.scala.refactoring.introduceVariable

import com.intellij.lang.{Language, LanguageUtil}
import com.intellij.openapi.project.Project
import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert.assertTrue

class IntroduceVariableValidatorInReplWorksheetTest extends TestCase

object IntroduceVariableValidatorInReplWorksheetTest {
  def suite(): Test = new AbstractIntroduceVariableValidatorTestBase("data_repl_worksheet") {
    override protected def getName(fileText: String): String = "value"

    //Using `findRegisteredLanguage` hack cause I don't want to move this test to worksheet module and the language is not available here
    override protected def language: Language = LanguageUtil.findRegisteredLanguage("Scala Worksheet")

    protected override def transform(testNameWithOptionalExtension: String, testFileText: String, project: Project): String = {
      val result = super.transform(testNameWithOptionalExtension, testFileText, project)

      val scalaFile = myFixture.psiFile.asInstanceOf[ScalaFile]
      assertTrue(
        "We expect that in the tested file it's allowed to use multiple declarations with same name",
        scalaFile.isMultipleDeclarationsAllowed
      )

      result
    }
  }
}
