package org.jetbrains.plugins.scala.worksheet

import com.intellij.psi.stubs.StubRegistry
import org.jetbrains.plugins.scala.lang.psi.stubs.ScalaStubRegistryExtensionAdapter

final class WorksheetStubRegistryExtension extends ScalaStubRegistryExtensionAdapter {
  override def register(registry: StubRegistry): Unit = {
    registerFileStubSerializer(registry, WorksheetParserDefinition.FileNodeType)
    registerFileStubSerializer(registry, WorksheetParserDefinition3.FileNodeType)
  }
}
