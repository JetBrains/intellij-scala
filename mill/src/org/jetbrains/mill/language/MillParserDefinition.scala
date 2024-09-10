package org.jetbrains.mill
package language

import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.parser.{ScalaParser, ScalaParserDefinitionBase}

final class MillParserDefinition extends ScalaParserDefinitionBase {

  override def createLexer(project: Project) = new ScalaLexer(false, project)

  override def createParser(project: Project) = new ScalaParser(isScala3 = false)

  override val getFileNodeType = new IFileElementType("mill.FILE", MillLanguage.INSTANCE)

  override def createFile(viewProvider: FileViewProvider) = new MillFileImpl(viewProvider)
}
