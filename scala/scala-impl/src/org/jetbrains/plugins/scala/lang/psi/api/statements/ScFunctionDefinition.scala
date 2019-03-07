package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, StaticTraitScFunctionWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.annotation.tailrec

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:36
*/
trait ScFunctionDefinition extends ScFunction with ScControlFlowOwner {

  import ScExpression._

  def body: Option[ScExpression]

  def hasAssign: Boolean

  def assignment: Option[PsiElement]

  def returnUsages: Set[ScExpression] = innerReturnUsages(calculateReturns)

  def recursiveReferences: Seq[RecursiveReference] = {
    def quickCheck(ref: ScReference): Boolean = {
      import ScFunction._
      val refName = ref.refName
      ref match {
        case _: ScStableCodeReference =>
          ref.getParent match {
            case ChildOf(_: ScConstructorInvocation) =>
              this.isConstructor && containingClass.name == refName
            case ScConstructorPattern(`ref`, _) |
                 ScInfixPattern(_, `ref`, _) => this.isUnapplyMethod
            case _ => false
          }
        case _: ScReferenceExpression if this.isApplyMethod =>
          containingClass match {
            case scObject: ScObject => scObject.name == refName
            case _ => true
          }
        case _: ScReferenceExpression => this.name == refName
        case _ => false
      }
    }

    val recursiveReferences = body.toSeq
      .flatMap(_.depthFirst())
      .collect {
        case element: ScReference if quickCheck(element) => element
      }.filter(_.isReferenceTo(this))

    val expressions: Set[PsiElement] = recursiveReferences match {
      case Seq() => Set.empty
      case _ =>
        def calculateExpandedReturns(expression: ScExpression): Set[ScExpression] = {
          val visitor: ReturnsVisitor = new ReturnsVisitor {

            private val booleanInstance = StdTypes.instance.Boolean

            override def visitInfixExpression(infix: ScInfixExpr): Unit = {
              infix match {
                case ScInfixExpr(Typeable(`booleanInstance`), ElementText("&&" | "||"), right@Typeable(`booleanInstance`)) =>
                  acceptVisitor(right)
                case _ => super.visitInfixExpression(infix)
              }
            }
          }

          expression.accept(visitor)
          visitor.result
        }

        def expandIf(expression: ScExpression): Set[ScExpression] = (expression match {
          case ScIf(_, Some(thenBranch), None) => calculateReturns(thenBranch).flatMap(expandIf)
          case _ => Set.empty
        }) ++ Set(expression)

        innerReturnUsages(calculateExpandedReturns).flatMap(expandIf)
    }

    @tailrec
    def possiblyTailRecursiveCallFor(element: PsiElement): PsiElement = element.getParent match {
      case call@(_: ScMethodCall |
                 _: ScGenericCall) => possiblyTailRecursiveCallFor(call)
      case infix: ScInfixExpr if infix.operation == element => possiblyTailRecursiveCallFor(infix)
      case statement: ScReturn => statement
      case _ => element
    }

    recursiveReferences.map { reference =>
      RecursiveReference(reference, expressions(possiblyTailRecursiveCallFor(reference)))
    }
  }

  override def controlFlowScope: Option[ScalaPsiElement] = body

  @Cached(ModCount.getBlockModificationCount, this)
  def getStaticTraitFunctionWrapper(cClass: PsiClassWrapper): StaticTraitScFunctionWrapper = {
    new StaticTraitScFunctionWrapper(this, cClass)
  }

  private def innerReturnUsages(calculateReturns: ScExpression => Set[ScExpression]): Set[ScExpression] = {
    def returnsIn(expression: ScExpression) =
      expression.depthFirst(!_.isInstanceOf[ScFunction]).collect {
        case statement: ScReturn => statement
      } ++ calculateReturns(expression)

    body.toSet
      .flatMap(returnsIn)
      .filter(_.getContainingFile == getContainingFile)
  }
}

object ScFunctionDefinition {

  object withBody {
    def unapply(fun: ScFunctionDefinition): Option[ScExpression] = Option(fun).flatMap(_.body)
  }

  implicit class FunctionDefinitionExt(private val function: ScFunctionDefinition) extends AnyVal {

    import RecursionType._

    def recursionType: RecursionType = function.recursiveReferences match {
      case Seq() => NoRecursion
      case seq if seq.forall(_.isTailCall) => TailRecursion
      case _ => OrdinaryRecursion
    }
  }
}

case class RecursiveReference(element: ScReference, isTailCall: Boolean)