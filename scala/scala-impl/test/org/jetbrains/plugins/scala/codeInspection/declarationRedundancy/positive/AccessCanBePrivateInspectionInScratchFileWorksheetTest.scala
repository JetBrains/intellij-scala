package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBePrivateInspectionTestBase

class AccessCanBePrivateInspectionInScratchFileWorksheetTest extends ScalaAccessCanBePrivateInspectionTestBase {

  //scala scratch files will be automatically treated as worksheets
  override protected val isScratchFile: Boolean = true

  def test_top_level_definition(): Unit = {
    checkTextHasNoErrors(
      """class DefinitionInScratchFileTopLevel
        |new DefinitionInScratchFileTopLevel()""".stripMargin
    )
  }

  def test_non_top_level_definition(): Unit = {
    checkTextHasError(
      s"""object DefinitionInScratchFileTopLevel {
         |  object ${START}DefinitionInScratchFileInner$END {
         |    val ${START}myValDefinitionLocal$END = 42
         |    println(myValDefinitionLocal)
         |  }
         |  println(DefinitionInScratchFileInner)
         |}
         |""".stripMargin,
    )
  }
}
