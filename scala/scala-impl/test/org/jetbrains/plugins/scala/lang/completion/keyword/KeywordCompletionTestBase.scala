package org.jetbrains.plugins.scala
package lang
package completion
package keyword

import java.io.File
import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.{LookupElementBuilder, LookupManager}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaKeywordLookupItem.KeywordInsertHandler
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert._
import org.junit.runner.RunWith

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

/**
 * @author Alexander Podkhalyuzin
 */
@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13, TestScalaVersion.Scala_3_0))
abstract class KeywordCompletionTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  def folderPath: String = baseRootPath + "keywordCompletion/"

  protected def doTest(): Unit = {
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull(s"file $filePath not found", file)

    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileTextAdapter(getTestName(false) + ".scala", fileText)

    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(EditorTestUtil.CARET_TAG)
    assertNotEquals(s"Caret marker not found.", offset, -1)

    val project = getProjectAdapter
    val editor = FileEditorManager.getInstance(project)
      .openTextEditor(new OpenFileDescriptor(project, getVFileAdapter, offset), false)
    new CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true)
      .invokeCompletion(project, editor)

    val items = LookupManager.getActiveLookup(editor) match {
      case impl: LookupImpl =>
        impl.getItems.asScala.filter {
          case item: LookupElementBuilder => item.getInsertHandler.isInstanceOf[KeywordInsertHandler]
          case _ => false
        }.map {
          _.getLookupString
        }
      case _ => Seq.empty
    }

    val actual = items.sorted.mkString("\n")

    val lastPsi = scalaFile.findElementAt(scalaFile.getTextLength - 1)
    val delta = lastPsi.getNode.getElementType match {
      case ScalaTokenTypes.tLINE_COMMENT => 0
      case ScalaTokenTypes.tBLOCK_COMMENT | ScalaTokenTypes.tDOC_COMMENT => 2
      case _ => -1
    }
    assertNotEquals("Test result must be in last comment statement.", delta, -1)

    val commentText = lastPsi.getText
    val text = commentText.substring(2, commentText.length - delta).trim
    val versionTag = if (scalaFile.isInScala3Module) "scala3" else "scala2"
    val keywords = text.linesIterator.flatMap(_.split(":") match {
      case Array(kw, `versionTag`) => Some(kw)
      case Array(_, _) => None
      case Array(kw) => Some(kw)
    }).toSeq
    val expectedText = keywords.sorted.mkString("\n")
    assertEquals(expectedText, actual.trim)
  }
}