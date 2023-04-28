package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScGivenAliasDefinitionImpl(
  stub:     ScFunctionStub[ScGivenAliasDefinition],
  nodeType: ScFunctionElementType[ScGivenAliasDefinition],
  node:     ASTNode
) extends ScFunctionDefinitionImpl(stub, nodeType, node)
    with ScGivenImpl
    with ScGivenAliasDefinition {

  override def toString: String = "ScGivenAliasDefinition: " + ifReadAllowed(name)("")

  override def returnType: TypeResult = typeElement.`type`()

  override def typeElement: ScTypeElement =
    byPsiOrStub(findChildByClassScala(classOf[ScTypeElement]))(_.typeElement.get)

  override def nameInner: String = {
    val explicitName = nameElement.map(_.getText)

    explicitName
      .getOrElse(ScalaPsiUtil.generateGivenOrExtensionName(typeElement))
  }

  override def nameId: PsiElement = nameElement.getOrElse(typeElement)

  override protected def keywordTokenType: IElementType = ScalaTokenType.GivenKeyword
}
