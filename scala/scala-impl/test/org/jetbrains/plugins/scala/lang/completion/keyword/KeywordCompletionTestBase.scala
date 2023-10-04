package org.jetbrains.plugins.scala.lang.completion.keyword

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElementBuilder, LookupManager}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaKeywordLookupItem.KeywordInsertHandler
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.plugins.scala.util.TestUtils.ExpectedResultFromLastComment
import org.jetbrains.plugins.scala.{CompletionTests, ScalaVersion}
import org.junit.Assert._
import org.junit.experimental.categories.Category

import java.io.File
import scala.jdk.CollectionConverters._

/**
 * See also:
 *  - [[org.jetbrains.plugins.scala.lang.completion3.ScalaKeywordCompletionTest]]
 *  - [[org.jetbrains.plugins.scala.lang.completion3.Scala3KeywordCompletionTest]]
 */
@Category(Array(classOf[CompletionTests]))
abstract class KeywordCompletionTestBase extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_2_13

  def folderPath: String = getTestDataPath + "keywordCompletion/"

  protected def doTest(): Unit = {
    val lowercaseFirstLetterOfTestFile = true
    val testFileName = getTestName(lowercaseFirstLetterOfTestFile) + ".scala"

    val filePath = folderPath + testFileName
    val testFile = LocalFileSystem.getInstance.findFileByPath(filePath.replace(File.separatorChar, '/'))
    assertNotNull(s"file $filePath not found", testFile)

    try {
      val fileText = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(testFile.getCanonicalPath), CharsetToolkit.UTF8))
      configureFromFileText(testFileName, fileText)

      val scalaFile = getFile.asInstanceOf[ScalaFile]
      val offset = fileText.indexOf(EditorTestUtil.CARET_TAG)
      assertNotEquals(s"Caret marker not found.", offset, -1)

      val project = getProject
      val editor = openEditorAtOffset(offset)

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

      val ExpectedResultFromLastComment(_, expected) = TestUtils.extractExpectedResultFromLastComment(scalaFile)
      
      assertEquals(expected.trim, actual.trim)
    } catch {
      case ex: Throwable =>
        System.err.println(s"Test file: $testFile")
        throw ex
    }
  }
}