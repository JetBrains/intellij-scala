package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl

final class Scala3ParserDefinition extends ScalaParserDefinitionBase {

  //noinspection TypeAnnotation
  override def getFileNodeType = ScalaElementType.Scala3File

  override def createLexer(project: Project) = new ScalaLexer(true, project)

  override def createFile(viewProvider: FileViewProvider) = new ScalaFileImpl(viewProvider)
}
