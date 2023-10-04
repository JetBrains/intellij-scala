package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElement, LookupManager}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.{CompletionTests, ScalaVersion}
import org.junit.Assert._
import org.junit.experimental.categories.Category

import java.io.File

@Category(Array(classOf[CompletionTests]))
abstract class FileTestDataCompletionTestBase extends ScalaLightCodeInsightFixtureTestCase {
  // Must be lazy so it can be overridden without early initializers
  protected lazy val caretMarker = "/*caret*/"
  protected lazy val extension: String = "scala"

  def folderPath: String = getTestDataPath + "completion/"

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_2_13

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

    configureFromFileText(fileName, fileText)

    val offset = fileText.indexOf(caretMarker) match {
      case -1 => throw new AssertionError(s"Not specified end marker in test case. Use $caretMarker in scala file for this.")
      case index => index
    }

    val editor = openEditorAtOffset(offset)

    val completionType = if (fileName.startsWith("Smart")) CompletionType.SMART else CompletionType.BASIC
    new CodeCompletionHandlerBase(
      completionType,
      false,
      false,
      true
    ).invokeCompletion(getProject, editor)

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

    val expected = TestUtils.extractExpectedResultFromLastComment(getFile).expectedResult
    checkResult(items, expected)
  }
}