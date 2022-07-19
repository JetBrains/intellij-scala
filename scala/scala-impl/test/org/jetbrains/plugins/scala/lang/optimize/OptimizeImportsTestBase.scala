package org.jetbrains.plugins.scala.lang.optimize

import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.{CharsetToolkit, LocalFileSystem}
import com.intellij.psi.{PsiComment, PsiFile}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.editor.importOptimizer.{OptimizeImportSettings, ScalaImportOptimizer}
import org.jetbrains.plugins.scala.extensions.{&&, ElementText, ElementType, StringExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.optimize.OptimizeImportsTestBase.OptimizeImportNotificationMessage
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.{assertEquals, fail}

import java.io.File
import scala.annotation.nowarn

@nowarn("msg=ScalaLightPlatformCodeInsightTestCaseAdapter")
abstract class OptimizeImportsTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  final protected def baseRootPath: String = TestUtils.getTestDataPath + "/"

  def folderPath: String = baseRootPath + "optimize/"

  protected def settings(file: PsiFile) = OptimizeImportSettings(file)

  def importOptimizer = new ScalaImportOptimizer() {
    override def settings(file: PsiFile): OptimizeImportSettings = OptimizeImportsTestBase.this.settings(file)
  }

  protected def doTest(before: String): Unit = {
    doTest(before, before, null)
  }

  protected def doTest(before: String, after: String, @Nullable expectedNotificationText: String): Unit = {
    configureFromFileText(getTestName(false) + ".scala", before)
    val scalaFile = getFile.asInstanceOf[ScalaFile]

    //why do we do that? SORT_IMPORTS is true by default
    if (getTestName(true).startsWith("sorted"))
      ScalaCodeStyleSettings.getInstance(getProject).setSortImports(true)

    val notification: Ref[String] = new Ref[String]

    executeWriteActionCommand(
      runAndGetNotification(importOptimizer.processFile(scalaFile), notification),
      "OptimiseImportsInTestsCommand",
      UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION
    )(getProject)

    val actual = scalaFile.getText
    assertEquals(after.withNormalizedSeparator, actual)
    assertEquals(expectedNotificationText, notification.get())
  }

  private def runAndGetNotification(runnable: Runnable, notificationRef: Ref[String]): Runnable = () => {
    runnable.run()
    val notification = runnable match {
      case cir: ImportOptimizer.CollectingInfoRunnable =>
        cir.getUserNotificationInfo
      case _ => null
    }
    notificationRef.set(notification)
  }

  protected def doTest(): Unit = {
    val (before, after, notificationText) = extractTestData
    doTest(before, after, notificationText.orNull)
  }

  private def extractTestData: (String, String, Option[String]) = {
    val fileText: String = {
      val filePath = folderPath + getTestName(false) + ".scala"
      val file = LocalFileSystem.getInstance.refreshAndFindFileByPath(filePath.replace(File.separatorChar, '/'))
      assert(file != null, "file " + filePath + " not found")
      FileUtil.loadFile(new File(file.getCanonicalPath), CharsetToolkit.UTF8).withNormalizedSeparator
    }
    extractTestData(fileText)
  }

  private def extractTestData(fileText: String): (String, String, Option[String]) = {
    // NOTE: only to extract before & after data
    configureFromFileText(s"${getTestName(false)}_temp.scala", fileText)
    val scalaFile = getFile.asInstanceOf[ScalaFile]

    val (firstComment, notificationMessage) = scalaFile.findElementAt(0) match {
      case comment@OptimizeImportNotificationMessage(message) => (comment, message)
      case _ =>
        fail("Optimize imports notification message must be in the first line comment").asInstanceOf[Nothing]
    }

    val lastComment = scalaFile.getLastChild match {
      case c@ElementType(ScalaTokenTypes.tBLOCK_COMMENT) => c
      case _ =>
        fail("Test result must be in last block comment statement.").asInstanceOf[Nothing]
    }

    val text = scalaFile.getText
    val before =
      text.substring(firstComment.getTextRange.getEndOffset, lastComment.getNode.getStartOffset)
        .stripPrefix("\n")
        .stripTrailing
    val after = lastComment.getText.stripPrefix("/*").stripSuffix("*/").stripPrefix("\n").stripTrailing

    (before, after, notificationMessage)
  }
}

object OptimizeImportsTestBase {
  private object OptimizeImportNotificationMessage {
    def unapply(element: PsiComment): Option[Option[String]] = element match {
      case ElementType(ScalaTokenTypes.tLINE_COMMENT) && ElementText(text) if text.startsWith(MESSAGE_COMMENT_START) =>
        val maybeMessage = text.substring(MESSAGE_COMMENT_START.length).trim match {
          case "null" => None
          case message => Some(message)
        }
        Some(maybeMessage)
      case _ => None
    }

    private val MESSAGE_COMMENT_START: String = "// Notification message:"
  }
}
