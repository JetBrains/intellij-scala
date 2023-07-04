package org.jetbrains.plugins.scala.refactoring.introduceVariable

import com.intellij.openapi.project.Project
import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert.assertTrue

class IntroduceVariableValidatorInReplWorksheetTest extends TestCase

object IntroduceVariableValidatorInReplWorksheetTest {
  def suite(): Test = new AbstractIntroduceVariableValidatorTestBase("data_repl_worksheet") {
    override protected def getName(fileText: String): String = "value"

    override protected def fileExtension: String = "sc"

    protected override def transform(testNameWithOptionalExtension: String, fileText: String, project: Project): String = {
      val result = super.transform(testNameWithOptionalExtension, fileText, project)

      assertTrue(
        "We expect that in the tested file it's allowed to use multiple declarations with same name",
        myFile.asInstanceOf[ScalaFile].isMultipleDeclarationsAllowed
      )

      result
    }
  }
}
