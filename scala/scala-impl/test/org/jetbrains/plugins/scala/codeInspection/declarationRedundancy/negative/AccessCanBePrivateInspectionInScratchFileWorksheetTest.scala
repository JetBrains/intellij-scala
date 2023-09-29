package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBePrivateInspectionTestBase

class AccessCanBePrivateInspectionInScratchFileWorksheetTest extends ScalaAccessCanBePrivateInspectionTestBase {

  //scala scratch files will be automatically treated as worksheets
  override protected val isScratchFile: Boolean = true

  def test_top_level_definition_and_member(): Unit =
    checkTextHasNoErrors(
      """class DefinitionInScratchFileTopLevel {
        |  val aMemberThatCanBePrivate = 42
        |  println(someMemberThatCanBePrivate)
        |}
        |new DefinitionInScratchFileTopLevel()""".stripMargin
    )
}
