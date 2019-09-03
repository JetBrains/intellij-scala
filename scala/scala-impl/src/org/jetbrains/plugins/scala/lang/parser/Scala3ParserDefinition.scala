package org.jetbrains.plugins.scala
package lang
package parser

import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

final class Scala3ParserDefinition extends ScalaParserDefinitionBase(
  new ScStubFileElementType("scala3.file", Scala3Language.INSTANCE)
) {
  override def createFile(viewProvider: FileViewProvider): ScalaFile =
    new ScalaFileImpl(viewProvider)
}
