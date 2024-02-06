package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType

class ScGivenAliasDefinitionImpl(
  stub:     ScFunctionStub[ScGivenAliasDefinition],
  nodeType: ScFunctionElementType[ScGivenAliasDefinition],
  node:     ASTNode
) extends ScFunctionDefinitionImpl(stub, nodeType, node)
    with ScGivenAliasDeclarationOrDefinitionImpl
    with ScGivenAliasDefinition {

  override def toString: String = "ScGivenAliasDefinition: " + ifReadAllowed(name)("")

  override protected def keywordTokenType: IElementType = ScalaTokenType.GivenKeyword

  override def isEffectivelyFinal: Boolean = true
}
