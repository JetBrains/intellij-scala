package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiFile
import junit.framework.Test
import org.jetbrains.plugins.scala.FileSetTests
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderExtendedMock, Message2, ScalaHighlightingTestLike}
import org.jetbrains.plugins.scala.base.{ScalaFileSetTestCase, ScalaLightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt, executeWriteActionCommand}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.project.ModuleExt
import org.junit.Assert.assertEquals
import org.junit.experimental.categories.Category

import java.nio.file.Path
import scala.jdk.CollectionConverters.ListHasAsScala

abstract class ScStringLiteralAnnotatorTestBase(testDataPath: String)
  extends ScalaFileSetTestCase(testDataPath) {

  override protected def needsSdk: Boolean = true

  protected def addCompilerOptions(module: Module, additionalCompilerOptions: Seq[String]): Unit = {
    val profile = module.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(additionalCompilerOptions = additionalCompilerOptions)
    profile.setSettings(newSettings)
  }

  override protected def constructTestCase(testFile: Path): Test =
    new MyTest(testFile)

  //noinspection JUnitMalformedDeclaration
  @Category(Array(classOf[FileSetTests]))
  private final class MyTest(override val myTestFile: Path)
    extends ScalaLightCodeInsightFixtureTestCase
      with ScalaLightCodeInsightFixtureTest_ForFileTestTests
      with ScalaHighlightingTestLike {

    override def fileBasedTest: ScalaFileSetTestCase = ScStringLiteralAnnotatorTestBase.this

    override protected def runTestForTestFileText(testFileText: String): Unit = {
      val fileParts = parseTestFileText(testFileText).asScala.toSeq
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
          quickFixes.foreach(_.asIntention().invoke(getProject, getEditor, getFile))
        }(getProject)

        getEditor.getDocument.commit(getProject)

        assertEquals(
          "Text after applying quick fixes",
          expectedTextAfterApplyingQuickFixes,
          getFile.getText
        )
      }
    }
  }

  private def collectMessages(file: PsiFile): List[Message2] = {
    val mock = new AnnotatorHolderExtendedMock(file)
    val literals = file.depthFirst().filterByType[ScStringLiteral].toSeq
    literals.foreach(ElementAnnotator.annotate(_, typeAware = true)(mock))
    mock.annotations
  }
}
