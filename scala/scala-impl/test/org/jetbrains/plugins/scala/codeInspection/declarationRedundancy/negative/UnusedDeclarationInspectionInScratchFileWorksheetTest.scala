package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import com.intellij.openapi.fileTypes.{FileTypeRegistry, LanguageFileType}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class UnusedDeclarationInspectionInScratchFileWorksheetTest extends ScalaUnusedDeclarationInspectionTestBase {

  //scala scratch files will be automatically treated as worksheets
  override protected val isScratchFile: Boolean = true

  override protected lazy val fileType: LanguageFileType =
    FileTypeRegistry.getInstance().getFileTypeByExtension("sc").asInstanceOf[LanguageFileType]

  def test_top_level_definition(): Unit =
    checkTextHasNoErrors("class DefinitionInScratchFileTopLevel")
}
