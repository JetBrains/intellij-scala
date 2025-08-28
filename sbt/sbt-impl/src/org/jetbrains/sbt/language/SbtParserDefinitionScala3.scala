package org.jetbrains.sbt.language

import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.parser.{ScalaParser, ScalaParserDefinitionBase}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

final class SbtParserDefinitionScala3 extends ScalaParserDefinitionBase {
  override def createFile(viewProvider: FileViewProvider): ScalaFile = new SbtFileImpl(viewProvider)

  override def createLexer(project: Project): Lexer = new ScalaLexer(/* isScala3 = */ true, project)

  override def createParser(project: Project): PsiParser = new ScalaParser(isScala3 = true)

  override val getFileNodeType: IFileElementType = new IFileElementType("sbt.FILE", SbtLanguageScala3.INSTANCE)
}
