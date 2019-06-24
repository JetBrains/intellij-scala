package org.jetbrains.sbt
package language

import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinitionBase
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

final class SbtParserDefinition extends ScalaParserDefinitionBase(
  new ScStubFileElementType("sbt.file", SbtLanguage.INSTANCE)
) {
  override def createFile(viewProvider: FileViewProvider) = new SbtFileImpl(viewProvider)
}
