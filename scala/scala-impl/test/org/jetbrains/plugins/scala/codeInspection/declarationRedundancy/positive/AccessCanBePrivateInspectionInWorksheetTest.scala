package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import com.intellij.openapi.fileTypes.{FileTypeRegistry, LanguageFileType}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBePrivateInspectionTestBase

class AccessCanBePrivateInspectionInWorksheetTest extends ScalaAccessCanBePrivateInspectionTestBase {

  override protected val isScratchFile: Boolean = false

  override protected lazy val fileType: LanguageFileType =
    FileTypeRegistry.getInstance().getFileTypeByExtension("sc").asInstanceOf[LanguageFileType]

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
