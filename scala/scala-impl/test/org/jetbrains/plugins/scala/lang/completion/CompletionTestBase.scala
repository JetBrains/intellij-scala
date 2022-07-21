package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElement, LookupManager}
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert._
import org.junit.experimental.categories.Category

import java.io.File
import scala.annotation.nowarn

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.09.2009
 */
@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
@Category(Array(classOf[CompletionTests]))
abstract class CompletionTestBase extends base.ScalaLightPlatformCodeInsightTestCaseAdapter {
  // Must be lazy so it can be overriden without early initializers
  protected lazy val caretMarker = "/*caret*/"
  protected lazy val extension: String = "scala"

  def folderPath: String = baseRootPath + "completion/"

  /**
   * Fetches last PSI element, checks if it is comment or not
   * If it is some kind of comment, treat it like an expected result string
   * If it's not, fail and return empty string
   *
   * @return Expected result string
   */
  protected final def getExpectedResult: String = {
    import lang.lexer.ScalaTokenTypes._

    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val lastPsi = scalaFile.findElementAt(scalaFile.getText.length - 1)

    val trimRight = lastPsi.getNode.getElementType match {
      case `tLINE_COMMENT` => 0
      case `tBLOCK_COMMENT` |
           `tDOC_COMMENT` => 2
      case _ =>
        throw new AssertionError("Test result must be in last comment statement.")
    }

    val text = lastPsi.getText
    text.substring(2, text.length - trimRight).trim
  }

  protected def checkResult(variants: Array[String], expected: String): Unit = {
    val actual = variants.sortWith(_ < _)
      .mkString("\n").trim
    assertEquals(expected, actual)
  }

  protected def doTest(): Unit = {
    val fileName = getTestName(false) + s".$extension"
    val filePath = s"$folderPath$fileName".replace(File.separatorChar, '/')

    val file = LocalFileSystem.getInstance.findFileByPath(filePath)
    assertNotNull(s"file '$filePath' not found", file)

    val fileText = StringUtil.convertLineSeparators(
      FileUtil.loadFile(
        new File(file.getCanonicalPath),
        CharsetToolkit.UTF8
      )
    )

    configureFromFileTextAdapter(fileName, fileText)

    val offset = fileText.indexOf(caretMarker) match {
      case -1 => throw new AssertionError(s"Not specified end marker in test case. Use $caretMarker in scala file for this.")
      case index => index
    }

    val project = getProjectAdapter
    val editor = FileEditorManager.getInstance(project)
      .openTextEditor(new OpenFileDescriptor(project, getVFileAdapter, offset), false)

    val completionType = if (fileName.startsWith("Smart")) CompletionType.SMART else CompletionType.BASIC
    new CodeCompletionHandlerBase(
      completionType,
      false,
      false,
      true
    ).invokeCompletion(project, editor)

    val items = LookupManager.getActiveLookup(editor) match {
      case lookup: LookupImpl =>
        val items = lookup.getItems.toArray(LookupElement.EMPTY_ARRAY)
        // TODO: test completion items presentations instead of just getLookupString
        //  something like:
        //  val presentations: Array[LookupElementPresentation] = items.map { item =>
        //    val presentation = new LookupElementPresentation
        //    item.renderElement(presentation)
        //    presentation
        //  }
        //  val itemTexts = presentations.map(_.getItemText)
        val lookups = items.map(_.getLookupString)
        lookups
      case _ => Array.empty[String]
    }

    val expected = getExpectedResult
    checkResult(items, expected)
  }
}