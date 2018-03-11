package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, api}
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * @author Alexander Podkhalyuzin
  */
class ScFunctionExprImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScFunctionExpr {

  def parameters: Seq[ScParameter] = params.params

  def params: ScParameters = findChildByClass(classOf[ScParameters])

  def result: Option[ScExpression] = findChild(classOf[ScExpression])

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    result match {
      case Some(x) if x == lastParent || (lastParent.isInstanceOf[ScalaPsiElement] &&
        x == lastParent.asInstanceOf[ScalaPsiElement].getDeepSameElementInContext) =>
        for (p <- parameters) {
          if (!processor.execute(p, state)) return false
        }
        true
      case _ => true
    }
  }

  protected override def innerType: TypeResult = {
    val paramTypes = parameters.map(_.`type`().getOrNothing)
    val maybeResultType = result.map(_.`type`().map(ScLiteralType.widen).getOrAny)
    val functionType = FunctionType(maybeResultType.getOrElse(api.Unit), paramTypes)
    Right(functionType)
  }

  override def controlFlowScope: Option[ScalaPsiElement] = result

  override def toString: String = "FunctionExpression"
}