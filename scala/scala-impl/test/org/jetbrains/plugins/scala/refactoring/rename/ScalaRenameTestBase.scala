package org.jetbrains.plugins.scala
package refactoring.rename

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.refactoring.rename.{RenameProcessor, RenamePsiElementProcessor}
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.TestUtils

import java.io.File
import scala.annotation.nowarn

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
abstract class ScalaRenameTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  val caretMarker = "/*caret*/"

  protected def folderPath: String = TestUtils.getTestDataPath + "/rename/"

  protected def doTest(): Unit = {
    import org.junit.Assert._
    val filePath = folderPath + getTestName(false) + ".scala"
    val ioFile: File = new File(filePath)
    var fileText: String = FileUtil.loadFile(ioFile, CharsetToolkit.UTF8)
    fileText = StringUtil.convertLineSeparators(fileText)
    configureFromFileTextAdapter(ioFile.getName, fileText)
    val scalaFile: ScalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(caretMarker) + caretMarker.length + 1
    assert(offset != caretMarker.length, "Not specified caret marker in test case. Use /*caret*/ in scala file for this.")
    getEditorAdapter.getCaretModel.moveToOffset(offset)
    val element = TargetElementUtil.findTargetElement(
      InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(getEditorAdapter, scalaFile): @nowarn("cat=deprecation"),
      TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil.ELEMENT_NAME_ACCEPTED)
    assert(element != null, "Reference is not specified.")
    val searchInComments = element.getText.contains("Comments")

    var res: String = null
    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)

    //start to inline
    executeWriteActionCommand("Test") {
      val subst = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, getEditorAdapter)
      if (subst == null) return
      new RenameProcessor(getProjectAdapter, subst, "NameAfterRename", searchInComments, false).run()
    }(getProjectAdapter)
    res = scalaFile.getText.substring(0, lastPsi.getTextOffset).trim

    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ =>
        assertTrue("Test result must be in last comment statement.", false)
        ""
    }
    assertEquals(output, res)
  }
}