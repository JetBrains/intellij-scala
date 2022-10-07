package org.jetbrains.plugins.scala
package refactoring
package introduceVariable

import com.intellij.openapi.project.Project
import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler

class IntroduceVariableSuggestNamesTest extends TestCase

object IntroduceVariableSuggestNamesTest {
  def suite(): Test = new AbstractIntroduceVariableValidatorTestBase("suggestNames") {
    override protected def needsSdk: Boolean = true

    override protected def doTest(replaceAllOccurrences: Boolean, fileText: String,
                                  project: Project): String = {
      val startOffset = myEditor.getSelectionModel.getSelectionStart
      val endOffset = myEditor.getSelectionModel.getSelectionEnd
      new ScalaIntroduceVariableHandler()
        .suggestedNamesForExpression(myFile, startOffset, endOffset)(myFile.getProject, myEditor)
        .mkString("\n")
    }

    override protected def getName(fileText: String): String = ???
  }
}