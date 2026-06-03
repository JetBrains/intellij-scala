package org.jetbrains.sbt
package language

import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.parser.{ScalaParser, ScalaParserDefinitionBase}

final class SbtParserDefinition extends ScalaParserDefinitionBase {

  override def createLexer(project: Project) = new ScalaLexer(false, project)

  override def createParser(project: Project) = new ScalaParser(isScala3 = false)

  override val getFileNodeType = new IFileElementType("sbt.FILE", SbtLanguage.INSTANCE)

  override def createFile(viewProvider: FileViewProvider) = new SbtFileImpl(viewProvider)
}
