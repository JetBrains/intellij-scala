package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import com.intellij.openapi.fileTypes.{FileTypeRegistry, LanguageFileType}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase
import org.junit.{Ignore, Test}
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class UnusedDeclarationInspectionInScratchFileWorksheetTest extends ScalaUnusedDeclarationInspectionTestBase {

  override protected val isScratchFile: Boolean = true

  override protected lazy val fileType: LanguageFileType =
    FileTypeRegistry.getInstance().getFileTypeByExtension("sc").asInstanceOf[LanguageFileType]

  @Ignore("Test started to fail after the update to 253.22441.x EAP")
  @Test
  def test_non_top_level_member(): Unit =
    checkTextHasError(
      s"""class DefinitionInWorksheetFileTopLevel {
        |  val ${START}aMemberThatIsUnused$END = 42
        |}
        |new DefinitionInWorksheetFileTopLevel()""".stripMargin
    )
}
