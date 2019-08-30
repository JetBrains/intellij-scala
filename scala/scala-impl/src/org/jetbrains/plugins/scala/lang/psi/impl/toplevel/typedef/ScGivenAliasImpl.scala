package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenAlias, ScGivenInstance}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPropertyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScPropertyElementType
import org.jetbrains.plugins.scala.lang.psi.types.ScLiteralType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScGivenAliasImpl private[psi](stub: ScPropertyStub[ScGivenAlias],
                                    nodeType: ScPropertyElementType[ScGivenAlias],
                                    node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScGivenAlias with ScGivenInstanceImpl {

  override def expr: Option[ScExpression] =
    byPsiOrStub(findChild(classOf[ScExpression]))(_.bodyExpression)

  override def flavor: ScGivenInstance.Flavor = ScGivenInstance.GivenAlias

  override protected def keywordElementType: IElementType = ScalaTokenType.Given

  override def isAbstract: Boolean = false

  override def typeElement: Option[ScTypeElement] =
    byPsiOrStub(findChild(classOf[ScTypeElement]))(_.typeElement)

  override def `type`(): TypeResult = typeElement match {
    case Some(te) => te.`type`()
    case None =>
      expr
        .map(_.`type`().map(ScLiteralType.widenRecursive))
        .getOrElse(Failure("Cannot infer type without an expression"))
  }
}
