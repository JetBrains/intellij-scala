package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.LanguageStubDefinition
import org.jetbrains.plugins.scala.lang.parser.{Scala3ParserDefinition, ScalaParserDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubFileElementType

abstract class ScalaLanguageStubDefinitionBase[FileType <: ScStubFileElementType](fileType: FileType) extends LanguageStubDefinition {
  override def getStubVersion: Int = fileType.stubVersion

  override def getBuilder: StubBuilder = fileType.stubBuilder

  override def shouldBuildStubFor(file: VirtualFile): Boolean = fileType.shouldBuildStubFor(file)
}

final class ScalaLanguageStubDefinition extends ScalaLanguageStubDefinitionBase(ScalaParserDefinition.FileNodeType)
final class Scala3LanguageStubDefinition extends ScalaLanguageStubDefinitionBase(Scala3ParserDefinition.FileNodeType)
