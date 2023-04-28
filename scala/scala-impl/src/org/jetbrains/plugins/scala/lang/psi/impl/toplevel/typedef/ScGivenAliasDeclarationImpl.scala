package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDeclarationImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScGivenAliasDeclarationImpl(
  stub:     ScFunctionStub[ScGivenAliasDeclaration],
  nodeType: ScFunctionElementType[ScGivenAliasDeclaration],
  node:     ASTNode
) extends ScFunctionDeclarationImpl(stub, nodeType, node)
    with ScGivenImpl
    with ScGivenAliasDeclaration {
  override def toString: String = "ScGivenAliasDeclaration: " + ifReadAllowed(name)("")

  override def returnType: TypeResult = typeElement.`type`()

  override def typeElement: ScTypeElement =
    byPsiOrStub(findChildByClassScala(classOf[ScTypeElement]))(_.typeElement.get)

  override def nameInner: String = {
    val explicitName = nameElement.map(_.getText)

    explicitName
      .getOrElse(ScalaPsiUtil.generateGivenOrExtensionName(typeElement))
  }

  override def nameId: PsiElement = nameElement.getOrElse(typeElement)
}
