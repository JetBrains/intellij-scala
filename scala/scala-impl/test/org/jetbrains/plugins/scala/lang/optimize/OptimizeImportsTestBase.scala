package org.jetbrains.plugins.scala
package lang.optimize

import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.editor.importOptimizer.{OptimizeImportSettings, ScalaImportOptimizer}
import org.jetbrains.plugins.scala.extensions.{ElementType, StringExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.{assertEquals, fail}
import org.junit.experimental.categories.Category

import java.io.File
import scala.annotation.nowarn

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
@Category(Array(classOf[LanguageTests]))
abstract class OptimizeImportsTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  final protected def baseRootPath: String = TestUtils.getTestDataPath + "/"

  def folderPath: String = baseRootPath + "optimize/"

  protected def settings(file: PsiFile) = OptimizeImportSettings(file)

  def importOptimizer = new ScalaImportOptimizer() {
    override def settings(file: PsiFile): OptimizeImportSettings = OptimizeImportsTestBase.this.settings(file)
  }

  protected def doTest(before: String): Unit = {
    doTest(before, before)
  }

  protected def doTest(before: String, after: String): Unit = {
    configureFromFileText(getTestName(false) + ".scala", before)
    val scalaFile = getFile.asInstanceOf[ScalaFile]

    //why do we do that? SORT_IMPORTS is true by default
    if (getTestName(true).startsWith("sorted"))
      ScalaCodeStyleSettings.getInstance(getProject).setSortImports(true)

    executeWriteActionCommand(
      importOptimizer.processFile(scalaFile),
      "OptimiseImportsInTestsCommand",
      UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION
    )(getProject)

    val actual = scalaFile.getText
    assertEquals(after.withNormalizedSeparator, actual)
  }

  protected def doTest(): Unit = {
    val (before, after) = extractTestData
    doTest(before, after)
  }

  private def extractTestData: (String, String) = {
    val fileText: String = {
      val filePath = folderPath + getTestName(false) + ".scala"
      val file = LocalFileSystem.getInstance.refreshAndFindFileByPath(filePath.replace(File.separatorChar, '/'))
      assert(file != null, "file " + filePath + " not found")
      FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8).withNormalizedSeparator
    }
    extractTestData(fileText)
  }

  private def extractTestData(fileText: String): (String, String) = {
    // NOTE: only to extract before & after data
    configureFromFileText(s"${getTestName(false)}_temp.scala", fileText)
    val scalaFile = getFile.asInstanceOf[ScalaFile]

    val lastComment = scalaFile.getLastChild match {
      case c@ElementType(ScalaTokenTypes.tBLOCK_COMMENT) => c
      case _ =>
        fail("Test result must be in last block comment statement.").asInstanceOf[Nothing]
    }

    val text = scalaFile.getText
    val before = text.substring(0, lastComment.getNode.getStartOffset).stripTrailing
    val after = lastComment.getText.stripPrefix("/*").stripSuffix("*/").stripPrefix("\n").stripTrailing

    (before, after)
  }
}