package org.jetbrains.plugins.scala
package lang.completion

import org.jetbrains.plugins.scala.base.ScalaPsiTestCase
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import com.intellij.psi.util.PsiTreeUtil
import java.io.File
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.util.Consumer
import com.intellij.psi.{PsiElement, PsiManager}
import collection.mutable.ArrayBuffer
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType, CompletionParameters, CompletionService}
import com.intellij.codeInsight.lookup.{LookupManager, LookupElement}
import com.intellij.codeInsight.lookup.impl.LookupImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.09.2009
 */

abstract class CompletionTestBase extends ScalaPsiTestCase {
  private val caretMarker = "/*caret*/"

  override def rootPath: String = super.rootPath + "completion/"

  protected def doTest = {
    import _root_.junit.framework.Assert._

    val testName = getTestName(false)
    val filePath = rootPath + testName + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    val scalaFile: ScalaFile = PsiManager.getInstance(myProject).findFile(file).asInstanceOf[ScalaFile]
    val fileText = scalaFile.getText
    val offset = fileText.indexOf(caretMarker)
    assert(offset != -1, "Not specified end marker in test case. Use /*caret*/ in scala file for this.")
    val fileEditorManager = FileEditorManager.getInstance(myProject)
    val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(myProject, file, offset), false)
    val myType = if (testName.startsWith("Smart")) CompletionType.SMART else CompletionType.BASIC
    new CodeCompletionHandlerBase(myType).invoke(myProject, editor, scalaFile)
    var lookup: LookupImpl = LookupManager.getActiveLookup(editor).asInstanceOf[LookupImpl]
    val items: Array[String] =
      if (lookup == null) Array.empty
      else lookup.getItems.toArray(LookupElement.EMPTY_ARRAY).map(_.getLookupString)

    val res = items.sortWith(_ < _).mkString("\n")
    
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