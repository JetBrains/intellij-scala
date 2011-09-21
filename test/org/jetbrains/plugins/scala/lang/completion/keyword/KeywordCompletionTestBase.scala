package org.jetbrains.plugins.scala.lang.completion.keyword

import org.jetbrains.plugins.scala.base.ScalaPsiTestCase
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.psi.PsiManager
import com.intellij.openapi.fileEditor.{OpenFileDescriptor, FileEditorManager}
import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElement, LookupManager}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * @author Alexander Podkhalyuzin
 */

abstract class KeywordCompletionTestBase extends ScalaPsiTestCase {
  private val caretMarker = "/*caret*/"

  override def rootPath: String = super.rootPath + "keywordCompletion/"

  protected def doTest {
    import _root_.junit.framework.Assert._

    val testName = getTestName(false)
    val filePath = rootPath + testName + ".scala"
    val file = LocalFileSystem.getInstance.refreshAndFindFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]
    val fileText = scalaFile.getText
    val offset = fileText.indexOf(caretMarker)
    assert(offset != -1, "Not specified end marker in test case. Use /*caret*/ in scala file for this.")
    val fileEditorManager = FileEditorManager.getInstance(myProject)
    val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, file, offset), false)
    val myType = CompletionType.BASIC
    new CodeCompletionHandlerBase(myType, true, false, true).invoke(myProject, editor, scalaFile)
    var lookup: LookupImpl = LookupManager.getActiveLookup(editor).asInstanceOf[LookupImpl]
    val items: Array[String] =
      if (lookup == null) Array.empty
      else lookup.getItems.toArray(LookupElement.EMPTY_ARRAY).map(_.getLookupString)

    val res = items.sortWith(_ < _).filter(ScalaNamesUtil.isKeyword(_)).mkString("\n")

    println("------------------------ " + scalaFile.getName + " ------------------------")
    println(res)
    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
    val text = lastPsi.getText
    val output = lastPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => text.substring(2).trim
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ => assertTrue("Test result must be in last comment statement.", false)
    }
    assertEquals(output, res.trim)
  }
}