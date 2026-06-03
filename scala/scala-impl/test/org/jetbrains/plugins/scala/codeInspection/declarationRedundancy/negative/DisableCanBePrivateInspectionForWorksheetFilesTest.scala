package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import com.intellij.openapi.fileTypes.{FileTypeRegistry, LanguageFileType}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBePrivateInspectionTestBase

class DisableCanBePrivateInspectionForWorksheetFilesTest extends ScalaAccessCanBePrivateInspectionTestBase {

  override protected lazy val fileType: LanguageFileType =
    FileTypeRegistry.getInstance().getFileTypeByExtension("sc").asInstanceOf[LanguageFileType]

  def test_can_be_private(): Unit = checkTextHasNoErrors(
    """class DoNotInspectWorksheetFilesTest { val doNotInspectMe = 42; println(doNotInspectMe) }
      |object DoNotInspectWorksheetFilesTest { new DoNotInspectWorksheetFilesTest() }
      |""".stripMargin)
}
