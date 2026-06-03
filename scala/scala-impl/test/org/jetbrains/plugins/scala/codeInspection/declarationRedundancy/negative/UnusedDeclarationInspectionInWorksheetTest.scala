package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import com.intellij.openapi.fileTypes.{FileTypeRegistry, LanguageFileType}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspectionTestBase

class UnusedDeclarationInspectionInWorksheetTest extends ScalaUnusedDeclarationInspectionTestBase {

  override protected val isScratchFile: Boolean = false

  override protected lazy val fileType: LanguageFileType =
    FileTypeRegistry.getInstance().getFileTypeByExtension("sc").asInstanceOf[LanguageFileType]

  def test_top_level_definition(): Unit =
    checkTextHasNoErrors("class DefinitionInScratchFileTopLevel")
}
