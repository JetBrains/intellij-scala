package org.jetbrains.plugins.scala.refactoring.rename

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.refactoring.rename.{RenameProcessor, RenamePsiElementProcessor}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PathExt, StringExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.refactoring.refactoringCommonTestDataRoot
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment

import java.nio.charset.StandardCharsets
import java.nio.file.Path

abstract class ScalaRenameTestBase extends ScalaLightCodeInsightFixtureTestCase {
  val caretMarker = "/*caret*/"

  protected def folderPath: Path = refactoringCommonTestDataRoot / "rename"

  protected def doTest(): Unit = {
    import org.junit.Assert._
    val fileName = getTestName(false) + ".scala"
    val filePath = folderPath / fileName
    var fileText: String = filePath.readAllBytesToString(StandardCharsets.UTF_8)
    fileText = fileText.withNormalizedSeparator
    configureFromFileText(fileName, fileText)
    val scalaFile: ScalaFile = getFile.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(caretMarker) + caretMarker.length + 1
    assert(offset != caretMarker.length, "Not specified caret marker in test case. Use /*caret*/ in scala file for this.")
    getEditor.getCaretModel.moveToOffset(offset)
    val element = TargetElementUtil.findTargetElement(
      InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(getEditor, scalaFile),
      TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil.ELEMENT_NAME_ACCEPTED)
    assert(element != null, "Reference is not specified.")
    val searchInComments = element.getText.contains("Comments")

    //start to inline
    executeWriteActionCommand("Test") {
      val subst = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, getEditor)
      if (subst == null) return
      new RenameProcessor(getProject, subst, "NameAfterRename", searchInComments, false).run()
    }(getProject)

    val ExpectedResultFromLastComment(res, output) = TestUtils.extractExpectedResultFromLastComment(getFile)

    assertEquals(output, res)
  }
}
