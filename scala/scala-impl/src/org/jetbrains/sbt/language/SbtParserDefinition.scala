package org.jetbrains.sbt
package language

import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinitionBase

final class SbtParserDefinition extends ScalaParserDefinitionBase {

  override val getFileNodeType = new IFileElementType("sbt.FILE", SbtLanguage.INSTANCE)

  override def createFile(viewProvider: FileViewProvider) = new SbtFileImpl(viewProvider)
}
