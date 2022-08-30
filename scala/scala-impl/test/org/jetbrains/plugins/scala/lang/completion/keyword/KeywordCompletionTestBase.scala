package org.jetbrains.plugins.scala
package lang
package completion
package keyword

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElementBuilder, LookupManager}
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaKeywordLookupItem.KeywordInsertHandler
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert._
import org.junit.experimental.categories.Category

import java.io.File
import scala.jdk.CollectionConverters._

@Category(Array(classOf[CompletionTests]))
abstract class KeywordCompletionTestBase extends ScalaLightCodeInsightFixtureTestCase {

  def folderPath: String = getTestDataPath + "keywordCompletion/"

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken(this.getClass.getName + "_" + this.version.toString)

  protected def doTest(): Unit = {
    val filePath = folderPath + getTestName(false) + ".scala"
    val file = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull(s"file $filePath not found", file)

    val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8))
    configureFromFileText(getTestName(false) + ".scala", fileText)

    val scalaFile = getFile.asInstanceOf[ScalaFile]
    val offset = fileText.indexOf(EditorTestUtil.CARET_TAG)
    assertNotEquals(s"Caret marker not found.", offset, -1)

    val editor = openEditorAtOffset(offset)

    new CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true)
      .invokeCompletion(getProject, editor)

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

    val text = lastPsi.getText
    assertEquals(text.substring(2, text.length - delta).trim, actual.trim)
  }
}