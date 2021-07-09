package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAlias
import org.jetbrains.plugins.scala.lang.psi.impl.statements.{ScFunctionDefinitionImpl, ScFunctionImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScGivenAliasImpl(
  stub:     ScFunctionStub[ScGivenAlias],
  nodeType: ScFunctionElementType[ScGivenAlias],
  node:     ASTNode
) extends ScFunctionDefinitionImpl(stub, nodeType, node)
    with ScGivenImpl
    with ScGivenAlias {

  override def toString: String = "ScGivenAlias: " + ifReadAllowed(name)("")

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
