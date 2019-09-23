package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl

final class ScalaParserDefinition extends ScalaParserDefinitionBase {

  //noinspection TypeAnnotation
  override def getFileNodeType = ScalaElementType.ScalaFile

  override def createFile(viewProvider: FileViewProvider) = new ScalaFileImpl(viewProvider)
}
