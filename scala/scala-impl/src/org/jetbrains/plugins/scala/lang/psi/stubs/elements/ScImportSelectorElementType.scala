package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportSelectorImpl

final class ScImportSelectorElementType extends ScStubElementType[ScImportSelector]("import selector") {
  override def createElement(node: ASTNode): ScImportSelector = new ScImportSelectorImpl(node)
}
