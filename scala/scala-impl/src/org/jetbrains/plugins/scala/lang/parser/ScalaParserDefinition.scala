package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

class ScalaParserDefinition extends ScalaParserDefinitionBase {

  override def createLexer(project: Project) = new ScalaLexer(false, project)

  override def createParser(project: Project) = new ScalaParser(isScala3 = false)

  //noinspection TypeAnnotation
  override def getFileNodeType = ScalaParserDefinition.FileNodeType

  override def createFile(viewProvider: FileViewProvider) = new ScalaFileImpl(viewProvider)
}

object ScalaParserDefinition {

  //noinspection TypeAnnotation
  val FileNodeType = ScStubFileElementType(ScalaLanguage.INSTANCE)
}
