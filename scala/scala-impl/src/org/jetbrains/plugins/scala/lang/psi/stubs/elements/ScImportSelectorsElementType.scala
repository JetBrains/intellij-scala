package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelectors
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportSelectorsImpl

final class ScImportSelectorsElementType extends ScStubElementType[ScImportSelectors]("import selectors") {
  override def createElement(node: ASTNode): ScImportSelectors = new ScImportSelectorsImpl(node)
}
