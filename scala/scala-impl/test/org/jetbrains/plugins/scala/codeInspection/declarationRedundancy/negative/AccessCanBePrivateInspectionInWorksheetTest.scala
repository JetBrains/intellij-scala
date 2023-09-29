package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import com.intellij.openapi.fileTypes.{FileTypeRegistry, LanguageFileType}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBePrivateInspectionTestBase

class AccessCanBePrivateInspectionInWorksheetTest extends ScalaAccessCanBePrivateInspectionTestBase {

  override protected val isScratchFile: Boolean = false

  override protected lazy val fileType: LanguageFileType =
    FileTypeRegistry.getInstance().getFileTypeByExtension("sc").asInstanceOf[LanguageFileType]

  def test_top_level_definition_and_member(): Unit =
    checkTextHasNoErrors(
      """class DefinitionInWorksheetFileTopLevel {
        |  val aMemberThatCanBePrivate = 42
        |  println(someMemberThatCanBePrivate)
        |}
        |new DefinitionInWorksheetFileTopLevel()""".stripMargin
    )
}
