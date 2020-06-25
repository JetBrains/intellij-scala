package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

final class Scala3ParserDefinition extends ScalaParserDefinitionBase {

  //noinspection TypeAnnotation
  override def getFileNodeType = Scala3ParserDefinition.FileNodeType

  override def createLexer(project: Project) = new ScalaLexer(true, project)

  override def createParser(project: Project): ScalaParser = new ScalaParser(isScala3 = true)

  override def createFile(viewProvider: FileViewProvider) = new ScalaFileImpl(viewProvider)
}

object Scala3ParserDefinition {

  //noinspection TypeAnnotation
  val FileNodeType = ScStubFileElementType(Scala3Language.INSTANCE)
}
