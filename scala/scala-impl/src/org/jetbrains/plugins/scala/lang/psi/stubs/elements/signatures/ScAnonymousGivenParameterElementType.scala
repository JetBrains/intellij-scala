package org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScAnonymousGivenParam, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScAnonymousGivenParamImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParameterStub

final class ScAnonymousGivenParameterElementType extends ScParamElementType[ScAnonymousGivenParam]("anonymous given parameter") {
  override def createElement(node: ASTNode): ScParameter = new ScAnonymousGivenParamImpl(node)

  override def createPsi(stub: ScParameterStub): ScParameter = new ScAnonymousGivenParamImpl(stub)
}