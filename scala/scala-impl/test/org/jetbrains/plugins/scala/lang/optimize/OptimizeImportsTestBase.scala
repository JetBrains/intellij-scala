package org.jetbrains.plugins.scala.lang.optimize

import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.jetbrains.plugins.scala.editor.importOptimizer.{OptimizeImportSettings, ScalaImportOptimizer}
import org.jetbrains.plugins.scala.extensions.{ElementType, StringExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert.{assertEquals, fail}

import java.io.File
import scala.annotation.nowarn

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.06.2009
 */
@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
abstract class OptimizeImportsTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  def folderPath: String = baseRootPath + "optimize/"

  protected def settings(file: PsiFile) = OptimizeImportSettings(file)

  def importOptimizer = new ScalaImportOptimizer() {
    override def settings(file: PsiFile): OptimizeImportSettings = OptimizeImportsTestBase.this.settings(file)
  }

  protected def doTest(before: String): Unit = {
    doTest(before, before)
  }

  protected def doTest(before: String, after: String): Unit = {
    configureFromFileTextAdapter(getTestName(false) + ".scala", before)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]

    //why do we do that? SORT_IMPORTS is true by default
    if (getTestName(true).startsWith("sorted"))
      ScalaCodeStyleSettings.getInstance(getProjectAdapter).setSortImports(true)

    executeWriteActionCommand(
      importOptimizer.processFile(scalaFile),
      "OptimiseImportsInTestsCommand",
      UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION
    )(getProjectAdapter)

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
    configureFromFileTextAdapter(s"${getTestName(false)}_temp.scala", fileText)
    val scalaFile = getFileAdapter.asInstanceOf[ScalaFile]

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