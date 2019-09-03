package org.jetbrains.sbt
package language

import com.intellij.psi.FileViewProvider
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinitionBase

final class SbtParserDefinition
  extends ScalaParserDefinitionBase("sbt.file", SbtLanguage.INSTANCE) {

  override def createFile(viewProvider: FileViewProvider) = new SbtFileImpl(viewProvider)
}
