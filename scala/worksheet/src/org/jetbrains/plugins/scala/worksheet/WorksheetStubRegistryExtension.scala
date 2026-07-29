package org.jetbrains.plugins.scala.worksheet

import com.intellij.psi.stubs.StubRegistry
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScalaFileStubSerializer, ScalaStubRegistryExtensionAdapter}

final class WorksheetStubRegistryExtension extends ScalaStubRegistryExtensionAdapter {
  override def register(registry: StubRegistry): Unit = {
    val fileType = WorksheetParserDefinition.FileNodeType
    val fileType3 = WorksheetParserDefinition3.FileNodeType

    registerStubSerializer(registry, fileType, new ScalaFileStubSerializer(fileType))
    registerStubSerializer(registry, fileType3, new ScalaFileStubSerializer(fileType3))
  }
}
