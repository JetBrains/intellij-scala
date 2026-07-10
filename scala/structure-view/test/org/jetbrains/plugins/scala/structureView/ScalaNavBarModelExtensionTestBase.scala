package org.jetbrains.plugins.scala.structureView

import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.junit.Assert.{assertTrue, fail}

abstract class ScalaNavBarModelExtensionTestBase extends ScalaLightCodeInsightFixtureTestCase {

  private val DefaultFileName = "aaa.scala"

  protected def assertNavBarPathAtCaret(@Language("Scala 3") code: String, expectedPath: Seq[String]): Unit = {
    assertNavBarPathAtCaretInFile(DefaultFileName, code, expectedPath)
  }

  protected def assertNavBarPathAtCaretInFile(fileName: String, @Language("Scala 3") code: String, expectedPath: Seq[String]): Unit = {
    val scalaFile = configureFromFileText(fileName, code).asInstanceOf[ScalaFile]
    assertNavBarPathAtCaretInConfiguredFile(scalaFile, expectedPath)
  }

  protected def assertNavBarPathAtCaretInFileWithLanguageId(
    fileName: String,
    expectedLanguageIds: Set[String],
    @Language("Scala 3") code: String,
    expectedPath: Seq[String]
  ): Unit = {
    val scalaFile = configureFromFileText(fileName, code).asInstanceOf[ScalaFile]
    val actualLanguageId = scalaFile.getLanguage.getID
    assertTrue(
      s"Expected $fileName language to be one of ${expectedLanguageIds.mkString(", ")}, but was $actualLanguageId",
      expectedLanguageIds.contains(actualLanguageId)
    )

    assertNavBarPathAtCaretInConfiguredFile(scalaFile, expectedPath)
  }

  protected def assertAdjustedNavBarPath(fileName: String, @Language("Scala 3") code: String, expectedPath: Seq[String]): Unit = {
    val scalaFile = configureFromFileText(fileName, code).asInstanceOf[ScalaFile]
    val navBarExtension = new ScalaNavBarModelExtension()
    val adjustedElement = Option(navBarExtension.adjustElement(scalaFile))
      .getOrElse(fail("No adjusted nav bar element").asInstanceOf[Nothing])
    val actualPath = navBarPath(navBarExtension, adjustedElement)

    assertCollectionEquals(expectedPath, actualPath)
  }

  private def assertNavBarPathAtCaretInConfiguredFile(scalaFile: ScalaFile, expectedPath: Seq[String]): Unit = {
    val navBarExtension = new ScalaNavBarModelExtension()
    val currentEditorElement = getCurrentEditorElement(scalaFile).asInstanceOf[PsiElement]
    val actualPath = navBarPath(navBarExtension, currentEditorElement)

    assertCollectionEquals(expectedPath, actualPath)
  }

  protected def navBarPath(navBarExtension: ScalaNavBarModelExtension, leafElement: PsiElement): Seq[String] =
    Iterator.iterate(leafElement)(navBarExtension.getParent)
      .takeWhile(_ != null)
      .map(navBarExtension.getPresentableText)
      .filter(_ != null)
      .toSeq
      .reverse

  protected def getCurrentEditorElement(scalaFile: ScalaFile): AnyRef = {
    val structureViewModel = new ScalaStructureViewBuilder(scalaFile).createStructureViewModel(getEditor)
    val currentElement = Option(structureViewModel.getCurrentEditorElement)
    currentElement.getOrElse(fail("No current editor element at caret").asInstanceOf[Nothing])
  }
}
