package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportExprImpl

final class ScImportExprElementType extends ScStubElementType[ScImportExpr]("import expression") {
  override def createElement(node: ASTNode): ScImportExpr = new ScImportExprImpl(node)
}
