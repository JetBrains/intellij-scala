package org.jetbrains.sbt.project

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.{PsiErrorElement, PsiFile, PsiManager}
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, executeUndoTransparentAction, invokeAndWait}
import org.jetbrains.plugins.scala.project.ProjectPsiFileExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.TrailingCommasMode
import org.jetbrains.plugins.scala.util.{RevertableChange, TestUtils}
import org.jetbrains.plugins.scala.{SlowTests, inWriteAction}
import org.jetbrains.sbt.project.HighlightingOfTrailingCommaErrorsInSbtFilesTest.{ParserError, withModifiedValue}
import org.junit.Assert
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.experimental.categories.Category

import java.io.File

@Category(Array(classOf[SlowTests]))
final class HighlightingOfTrailingCommaErrorsInSbtFilesTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/${getTestName(true)}"

  // Reminder: trailing commas are supported since 2.12.2
  def testTrailing_comma_sbt_0_13_scala_2_12_1(): Unit = testTrailingCommaErrors(
    hasErrorInBuildSources = true,
    hasErrorInMainSources = true
  )

  def testTrailing_comma_sbt_0_13_scala_2_12_2(): Unit = testTrailingCommaErrors(
    hasErrorInBuildSources = true,
    hasErrorInMainSources = false
  )

  def testTrailing_comma_sbt_1_5_scala_2_12_1(): Unit = testTrailingCommaErrors(
    hasErrorInBuildSources = false,
    hasErrorInMainSources = true
  )

  def testTrailing_comma_sbt_1_5_scala_2_12_2(): Unit = testTrailingCommaErrors(
    hasErrorInBuildSources = false,
    hasErrorInMainSources = false
  )

  private def testTrailingCommaErrors(
    hasErrorInBuildSources: Boolean,
    hasErrorInMainSources: Boolean
  ): Unit = {
    importProject(false)

    // it's enabled by default, but placing it here just to remind about the flag...
    ScalaProjectSettings.getInstance(myProject)
      .setTrailingCommasMode(TrailingCommasMode.Auto)

    val revertible = withModifiedValue[Boolean](
      true,
      () => ProjectPsiFileExt.enableFeaturesCheckInTests,
      ProjectPsiFileExt.enableFeaturesCheckInTests = _
    )
    revertible.run {
      val TrailingCommaErrorDescription = "Wrong expression"
      val expectedInBuildSbt = if (hasErrorInBuildSources) Seq(ParserError(186, TrailingCommaErrorDescription)) else Nil
      val expectedInBuildSources = if (hasErrorInBuildSources) Seq(ParserError(77, TrailingCommaErrorDescription)) else Nil
      val expectedInMainSources = if (hasErrorInMainSources) Seq(ParserError(76, TrailingCommaErrorDescription)) else Nil

      assertHasErrorsAfterAddingTrailingComma("/build.sbt", expectedInBuildSbt)
      assertHasErrorsAfterAddingTrailingComma("/project/BuildSourcesClass.scala", expectedInBuildSources)
      assertHasErrorsAfterAddingTrailingComma("/src/main/scala/MainSourcesClass.scala", expectedInMainSources)
    }
  }

  /**
   * NOTE: Original sbt files can't contain error trailing comma, because the project will not be imported.
   * So we insert the comma after the project is imported just before parsing the file.
   */
  private def assertHasErrorsAfterAddingTrailingComma(
    relativeFileName: String,
    expectedErrors: Seq[ParserError]
  ): Unit = {
    val buildSbtFile = findPsi(relativeFileName)
    insertTrailingCommaAfterMarker(buildSbtFile)
    val actualErrors = collectParserErrors(buildSbtFile)
    Assert.assertEquals(s"errors in `$relativeFileName` do not match", expectedErrors, actualErrors)
  }

  private def findPsi(relativeFileName: String): PsiFile = {
    val projectRoot = ProjectUtil.guessProjectDir(myProject)
    assertNotNull("project root not found", projectRoot)

    val nioFile = new File(projectRoot.getPath + relativeFileName).toPath
    val vFile = VirtualFileManager.getInstance().findFileByNioPath(nioFile)
    assertNotNull(s"file `$relativeFileName` wasn't found", vFile)

    val psi = PsiManager.getInstance(myProject).findFile(vFile)
    assertNotNull(s"PSI for file `$relativeFileName` wasn't found`", vFile)
    psi
  }

  private val TrailingCommaMarker = "\"TrailingCommaMarker\""

  private def insertTrailingCommaAfterMarker(file: PsiFile): Unit = {
    val document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile)
    val text = document.getText
    val markerIndex = text.indexOf(TrailingCommaMarker)
    assertTrue(s"trailing comma marker not found in:\n$text", markerIndex > 0)

    invokeAndWait {
      inWriteAction {
        executeUndoTransparentAction {
          document.insertString(markerIndex + TrailingCommaMarker.length, ",")
          document.commit(file.getProject)
        }
      }
    }
  }

  private def collectParserErrors(psi: PsiFile): Seq[ParserError] = {
    val errorElements = psi.depthFirst().collect { case err: PsiErrorElement => err }.toSeq
    errorElements.map(ParserError.fromErrorElement)
  }
}

object HighlightingOfTrailingCommaErrorsInSbtFilesTest {

  private case class ParserError(offset: Int, errorDescription: String)
  private object ParserError {
    def fromErrorElement(error: PsiErrorElement): ParserError =
      ParserError(error.startOffset, error.getErrorDescription)
  }

  private def withModifiedValue[T](newValue: T,
                                   getter: () => T,
                                   setter: T => Unit): RevertableChange =
    new RevertableChange {
      private var before: Option[T] = None

      override def applyChange(): Unit = {
        before = Some(getter())
        setter(newValue)
      }

      override def revertChange(): Unit = {
        before.foreach(setter)
      }
    }
}