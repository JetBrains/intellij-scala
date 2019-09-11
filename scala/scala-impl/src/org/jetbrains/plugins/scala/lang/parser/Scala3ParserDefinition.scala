package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl

final class Scala3ParserDefinition extends ScalaParserDefinitionBase {

  //noinspection TypeAnnotation
  override def getFileNodeType = ScalaElementType.Scala3File

  override def createFile(viewProvider: FileViewProvider) = new ScalaFileImpl(viewProvider)
}
