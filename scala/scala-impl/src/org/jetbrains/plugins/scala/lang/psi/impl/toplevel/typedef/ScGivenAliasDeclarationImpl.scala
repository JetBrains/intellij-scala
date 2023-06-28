package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDeclarationImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType

class ScGivenAliasDeclarationImpl(
  stub:     ScFunctionStub[ScGivenAliasDeclaration],
  nodeType: ScFunctionElementType[ScGivenAliasDeclaration],
  node:     ASTNode
) extends ScFunctionDeclarationImpl(stub, nodeType, node)
    with ScGivenAliasDeclarationOrDefinitionImpl
    with ScGivenAliasDeclaration {
  override def toString: String = "ScGivenAliasDeclaration: " + ifReadAllowed(name)("")
}
