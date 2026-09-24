package org.jetbrains.plugins.scala.lang.parser

import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

/**
 * @see [[org.jetbrains.plugins.scala.lang.parser.Scala3ParserDefinition]]
 */
class ScalaParserDefinition extends ScalaParserDefinitionBase {

  override def createLexer(project: Project): org.jetbrains.plugins.scala.lang.lexer.ScalaLexer = new ScalaLexer(false, project)

  override def createParser(project: Project): org.jetbrains.plugins.scala.lang.parser.ScalaParser = new ScalaParser(isScala3 = false)

  //noinspection TypeAnnotation
  override def getFileNodeType: org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType = ScalaParserDefinition.FileNodeType

  override def createFile(viewProvider: FileViewProvider): org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl = new ScalaFileImpl(viewProvider)
}

object ScalaParserDefinition {

  //noinspection TypeAnnotation
  val FileNodeType: ScStubFileElementType = ScStubFileElementType(ScalaLanguage.INSTANCE)
}
