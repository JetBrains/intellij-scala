package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.psi.FileViewProvider

final class ScalaParserDefinition extends ScalaParserDefinitionBase(
  new psi.stubs.elements.ScStubFileElementType("scala.file")
) {
  override def createFile(viewProvider: FileViewProvider) =
    new psi.impl.ScalaFileImpl(viewProvider)
}
