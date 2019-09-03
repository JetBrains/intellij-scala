package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl

final class Scala3ParserDefinition
  extends ScalaParserDefinitionBase("scala3.file", Scala3Language.INSTANCE) {

  override def createFile(viewProvider: FileViewProvider) = new ScalaFileImpl(viewProvider)
}
