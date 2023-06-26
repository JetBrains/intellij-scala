package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScGivenAliasDefinitionImpl(
  stub:     ScFunctionStub[ScGivenAliasDefinition],
  nodeType: ScFunctionElementType[ScGivenAliasDefinition],
  node:     ASTNode
) extends ScFunctionDefinitionImpl(stub, nodeType, node)
    with ScGivenImpl
    with ScGivenAliasDefinition {

  override def toString: String = "ScGivenAliasDefinition: " + ifReadAllowed(name)("")

  override def returnType: TypeResult =
    typeElement match {
      case Some(te) => te.`type`()
      case None => Failure(ScalaBundle.message("no.type.element.found", getText))
    }

  override def typeElement: Option[ScTypeElement] =
    byPsiOrStub(findChildByClassScala(classOf[ScTypeElement]).toOption)(_.typeElement)

  override def nameInner: String = {
    val explicitName = nameElement.map(_.getText)

    explicitName
      .getOrElse(ScalaPsiUtil.generateGivenOrExtensionName(typeElement.toSeq: _*))
  }

  override def nameId: PsiElement = nameElement.orElse(typeElement).orNull

  override protected def keywordTokenType: IElementType = ScalaTokenType.GivenKeyword
}
