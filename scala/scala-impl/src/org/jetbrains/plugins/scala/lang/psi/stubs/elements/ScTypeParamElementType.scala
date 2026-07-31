package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamImpl

final class ScTypeParamElementType extends ScStubElementType[ScTypeParam]("type parameter") {
  override def createElement(node: ASTNode): ScTypeParam = new ScTypeParamImpl(node)
}
