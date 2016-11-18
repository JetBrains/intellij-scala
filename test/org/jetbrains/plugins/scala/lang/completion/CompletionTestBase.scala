package org.jetbrains.plugins.scala
package lang.completion

import java.io.File

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElement, LookupManager}
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem, VirtualFile}
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.09.2009
 */

abstract class CompletionTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  protected val caretMarker = "/*caret*/"

  def folderPath: String = baseRootPath() + "completion/"
  def testFileExt: String = ".scala"

  protected def loadFile: (String, VirtualFile) = {
    val fileName = getTestName(false) + testFileExt
    val filePath = folderPath + fileName
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assert(file != null, "file " + filePath + " not found")
    (fileName, file)
  }

  protected def loadAndSetFileText(filePath: String, file: VirtualFile): String = {
    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(filePath, fileText)
    fileText
  }

  protected def extractCaretOffset(fileText: String): Int = {
    val offset = fileText.indexOf(caretMarker)
    assert(offset != -1, "Not specified end marker in test case. Use /*caret*/ in scala file for this.")
    offset
  }

  /**
   * Open {@code file} in editor at {@code offset} position,
   * perform completion and return results
   * @param file VFS file to open
   * @param offset Caret position
   * @return Array of lookup strings
   */
  protected def getCompletionItems(file: VirtualFile, offset: Integer): Array[String] = {
    val fileEditorManager = FileEditorManager.getInstance(getProjectAdapter)
    val editor = fileEditorManager.openTextEditor(new OpenFileDescriptor(getProjectAdapter, file, offset), false)

    val completionType = if (getTestName(false).startsWith("Smart")) CompletionType.SMART else CompletionType.BASIC
    new CodeCompletionHandlerBase(completionType, false, false, true).invokeCompletion(getProjectAdapter, editor)
    val lookup: LookupImpl = LookupManager.getActiveLookup(editor).asInstanceOf[LookupImpl]

    if (lookup == null)
      Array.empty
    else
      lookup.getItems.toArray(LookupElement.EMPTY_ARRAY).map(_.getLookupString)
  }

  /**
   * Fetches last PSI element, checks if it is comment or not
   * If it is some kind of comment, treat it like an expected result string
   * If it's not, fail and return empty string
   * @return Expected result string
   */
  protected def getExpectedResult: String = {
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)
    val text = lastPsi.getText
    lastPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT =>
        text.substring(2).trim
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT =>
        text.substring(2, text.length - 2).trim
      case _ =>
        assert(assertion = false, "Test result must be in last comment statement.")
        ""
    }
  }

  protected def checkResult(got: Array[String], expected: String) {
    import junit.framework.Assert._
    val res = got.sortWith(_ < _).mkString("\n")
    assertEquals(expected, res.trim)
  }

  protected def doTest() {
    val (filePath, file) = loadFile
    val fileText = loadAndSetFileText(filePath, file)
    val offset = extractCaretOffset(fileText)
    val items = getCompletionItems(file, offset)
    val expected = getExpectedResult
    checkResult(items, expected)
  }
}