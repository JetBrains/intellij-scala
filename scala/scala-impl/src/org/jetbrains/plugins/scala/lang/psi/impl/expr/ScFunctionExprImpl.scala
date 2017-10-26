package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

/**
 * @author Alexander Podkhalyuzin
 */

class ScFunctionExprImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionExpr {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "FunctionExpression"

  def parameters: Seq[ScParameter] = params.params

  def params: ScParameters = findChildByClass(classOf[ScParameters])

  def result: Option[ScExpression] = findChild(classOf[ScExpression])

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    result match {
      case Some(x) if x == lastParent || (lastParent.isInstanceOf[ScalaPsiElement] &&
        x == lastParent.asInstanceOf[ScalaPsiElement].getDeepSameElementInContext)=>
        for (p <- parameters) {
          if (!processor.execute(p, state)) return false
        }
        true
      case _ => true
    }
  }

  protected override def innerType: TypeResult[ScType] = {
    val paramTypes = parameters.map(_.`type`().getOrNothing)
    val maybeResultType = result.map(_.`type`().getOrAny)
    val functionType = FunctionType(maybeResultType.getOrElse(api.Unit), paramTypes)
    Right(functionType)
  }

  override def controlFlowScope: Option[ScalaPsiElement] = result
}