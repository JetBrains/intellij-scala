package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import com.intellij.openapi.fileTypes.{FileTypeRegistry, LanguageFileType}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class UnusedDeclarationInspectionInWorksheetTest extends ScalaUnusedDeclarationInspectionTestBase {

  override protected val isScratchFile: Boolean = false

  override protected lazy val fileType: LanguageFileType =
    FileTypeRegistry.getInstance().getFileTypeByExtension("sc").asInstanceOf[LanguageFileType]

  def test_non_top_level_member(): Unit =
    checkTextHasError(
      s"""class DefinitionInWorksheetFileTopLevel {
        |  val ${START}aMemberThatIsUnused$END = 42
        |}
        |new DefinitionInWorksheetFileTopLevel()""".stripMargin
    )
}
