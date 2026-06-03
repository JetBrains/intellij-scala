package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderExtendedMock, Message2}
import org.jetbrains.plugins.scala.base.SdkFileSetTestBase
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PathExt, PsiElementExt, StringExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettingsProfile
import org.junit.Assert.assertEquals

import java.nio.charset.StandardCharsets
import java.nio.file.Path

abstract class ScStringLiteralAnnotatorTestBase extends SdkFileSetTestBase {

  protected def addCompilerOptions(module: Module, additionalCompilerOptions: Seq[String]): Unit = {
    val profile = ScalaCompilerSettingsProfile.forModule(module)
    val newSettings = profile.getSettings.copy(additionalCompilerOptions = additionalCompilerOptions)
    profile.setSettings(newSettings)
  }

  override protected def transform(testName: String, fileText: String): String = fileText

  override protected def baseFileSetTest(testFile: Path): Unit = {
    val testFileText = testFile.readAllBytesToString(StandardCharsets.UTF_8).withNormalizedSeparator
    try {
      val fileParts = parseTestFileText(testFileText)
      val input = fileParts.head
      val expectedMessagesText = if (fileParts.size > 1) fileParts(1) else ""

      val file = configureFromFileText(input)
      val actualMessages = collectMessages(file)
      val actualMessagesText = actualMessages.map(_.textWithRangeAndMessage).mkString("\n")
      assertEquals("Messages text", expectedMessagesText, actualMessagesText)

      if (fileParts.size > 2) {
        val expectedTextAfterApplyingQuickFixes = fileParts(2)

        val quickFixes = actualMessages.flatMap(_.fixes)
        executeWriteActionCommand() {
          quickFixes.foreach(_.asIntention().invoke(project, getEditor, getFile))
        }(project)

        getEditor.getDocument.commit(project)

        assertEquals(
          "Text after applying quick fixes",
          expectedTextAfterApplyingQuickFixes,
          getFile.getText
        )
      }
    } catch {
      case error: Throwable =>
        // to be able to navigate to the original test file location on test failure
        // (you can use Ctrl/Cmd + Click in the console)
        // (note, might not work with Android plugin disabled, see IDEA-257969)
        System.err.println(s"### Test file: ${testFile.toAbsolutePath}")
        throw error
    }
  }

  private def collectMessages(file: PsiFile): List[Message2] = {
    val mock = new AnnotatorHolderExtendedMock(file)
    val literals = file.depthFirst().filterByType[ScStringLiteral].toSeq
    literals.foreach(ElementAnnotator.annotate(_, typeAware = true)(mock))
    mock.annotations
  }
}
