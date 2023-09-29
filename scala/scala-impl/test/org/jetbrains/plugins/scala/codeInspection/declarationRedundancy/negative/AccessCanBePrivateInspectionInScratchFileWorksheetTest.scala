package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import com.intellij.openapi.fileTypes.{FileTypeRegistry, LanguageFileType}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBePrivateInspectionTestBase

class AccessCanBePrivateInspectionInScratchFileWorksheetTest extends ScalaAccessCanBePrivateInspectionTestBase {

  //scala scratch files will be automatically treated as worksheets
  override protected val isScratchFile: Boolean = true

  override protected lazy val fileType: LanguageFileType =
    FileTypeRegistry.getInstance().getFileTypeByExtension("sc").asInstanceOf[LanguageFileType]

  def test_that_fails_in_order_to_prevent_merge(): Unit = throw new Exception

  def test_top_level_definition_and_member(): Unit =
    checkTextHasNoErrors(
      """class DefinitionInScratchFileTopLevel {
        |  val aMemberThatCanBePrivate = 42
        |  println(someMemberThatCanBePrivate)
        |}
        |new DefinitionInScratchFileTopLevel()""".stripMargin
    )
}
