package org.jetbrains.plugins.scala.lang.formatting.settings

import com.intellij.application.options.{SmartIndentOptionsEditor, IndentOptionsEditor}
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.codeStyle.CodeStyleSettings.IndentOptions
import com.intellij.psi.codeStyle.{CodeStyleSettings, FileTypeIndentOptionsProvider}
import com.intellij.psi.PsiFile

/**
 * @author ilyas
 */

class ScalaIndentOptionsProvider extends FileTypeIndentOptionsProvider {
  def getFileType: FileType = ScalaFileType.SCALA_FILE_TYPE

  def createOptionsEditor: IndentOptionsEditor = new SmartIndentOptionsEditor

  def createIndentOptions: IndentOptions = {
    val indentOptions = new CodeStyleSettings.IndentOptions
    indentOptions.INDENT_SIZE = 2
    indentOptions.TAB_SIZE = 2
    indentOptions
  }

  def getPreviewText: String = """
class A {
  def foo = 42 +
      239
}
""";


  def prepareForReformat(psiFile: PsiFile) {}
}