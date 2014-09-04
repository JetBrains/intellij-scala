package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElementVisitor, PsiField, ResolveState}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

/**
 * @author Alexander Podkhalyuzin
 */

class ScAssignStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScAssignStmt {
  override def toString: String = "AssignStatement"

  protected override def innerType(ctx: TypingContext) = {
    getLExpression match {
      case call: ScMethodCall => call.getType(ctx)
      case _ =>
        resolveAssignment match {
          case Some(resolveResult) =>
            mirrorMethodCall match {
              case Some(call) => call.getType(TypingContext.empty)
              case None => Success(Unit, Some(this))
            }
          case _ => Success(Unit, Some(this))
        }
    }
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  @volatile
  private var assignment: Option[ScalaResolveResult] = null

  @volatile
  private var assignmentModCount: Long = 0L

  def resolveAssignment: Option[ScalaResolveResult] = {
    val count = getManager.getModificationTracker.getModificationCount
    var res = assignment
    if (res != null && count == assignmentModCount) return assignment
    res = resolveAssignmentInner(shapeResolve = false)
    assignmentModCount = count
    assignment = res
    res
  }

  @volatile
  private var shapeAssignment: Option[ScalaResolveResult] = null

  @volatile
  private var shapeAssignmentModCount: Long = 0L

  def shapeResolveAssignment: Option[ScalaResolveResult] = {
    val count = getManager.getModificationTracker.getModificationCount
    var res = shapeAssignment
    if (res != null && count == shapeAssignmentModCount) return shapeAssignment
    res = resolveAssignmentInner(shapeResolve = true)
    shapeAssignmentModCount = count
    shapeAssignment = res
    res
  }

  @volatile
  private var methodCall: Option[ScMethodCall] = null

  @volatile
  private var methodCallModCount: Long = 0L

  def mirrorMethodCall: Option[ScMethodCall] = {
    def impl(): Option[ScMethodCall] = {
      getLExpression match {
        case ref: ScReferenceExpression =>
          val text = s"${ref.refName}_=(${getRExpression.map(_.getText).getOrElse("")})"
          val mirrorExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(text, getContext, this)
          mirrorExpr match {
            case call: ScMethodCall =>
              call.getInvokedExpr.asInstanceOf[ScReferenceExpression].setupResolveFunctions(
                () => resolveAssignment.toArray, () => shapeResolveAssignment.toArray
              )
              Some(call)
            case _ => None
          }
        case _ => None
      }
    }

    val count = getManager.getModificationTracker.getModificationCount
    var res = methodCall
    if (res != null && count == methodCallModCount) return methodCall
    res = impl()
    methodCallModCount = count
    methodCall = res
    res
  }

  private def resolveAssignmentInner(shapeResolve: Boolean): Option[ScalaResolveResult] = {
    getLExpression match {
      case ref: ScReferenceExpression =>
        ref.bind() match {
          case Some(r: ScalaResolveResult) =>
            ScalaPsiUtil.nameContext(r.element) match {
              case v: ScVariable => None
              case c: ScClassParameter if c.isVar => None
              case f: PsiField => None
              case fun: ScFunction if ScalaPsiUtil.isViableForAssignmentFunction(fun) =>
                val processor = new MethodResolveProcessor(ref, fun.name + "_=",
                  getRExpression.map(expr => List(Seq(new Expression(expr)))).getOrElse(Nil), Nil, ref.getPrevTypeInfoParams,
                  isShapeResolve = shapeResolve, kinds = StdKinds.methodsOnly)
                r.fromType match {
                  case Some(tp) => processor.processType(tp, ref)
                  case None =>
                    fun.getContext match {
                      case d: ScDeclarationSequenceHolder =>
                        d.processDeclarations(processor, ResolveState.initial(), fun, ref)
                      case _ =>
                    }
                }
                val candidates = processor.candidatesS
                if (candidates.size == 1) Some(candidates.toArray.apply(0))
                else None
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
  }
}