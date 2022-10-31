package org.jetbrains.plugins.scala.refactoring.rename

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.refactoring.rename.{RenameProcessor, RenamePsiElementProcessor}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.refactoring.refactoringCommonTestDataRoot
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment

import java.io.File
import scala.annotation.nowarn

abstract class ScalaRenameTestBase extends ScalaLightCodeInsightFixtureTestCase {
  val caretMarker = "/*caret*/"

  protected def folderPath: String = refactoringCommonTestDataRoot + "rename/"

  protected def doTest(): Unit = {
    import org.junit.Assert._
    val filePath = folderPath + getTestName(false) + ".scala"
    val ioFile: File = new File(filePath)
    var fileText: String = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    fileText = StringUtil.convertLineSeparators(fileText)
    configureFromFileText(ioFile.getName, fileText)
    val scalaFile: ScalaFile = getFile.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(caretMarker) + caretMarker.length + 1
    assert(offset != caretMarker.length, "Not specified caret marker in test case. Use /*caret*/ in scala file for this.")
    getEditor.getCaretModel.moveToOffset(offset)
    val element = TargetElementUtil.findTargetElement(
      InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(getEditor, scalaFile): @nowarn("cat=deprecation"),
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