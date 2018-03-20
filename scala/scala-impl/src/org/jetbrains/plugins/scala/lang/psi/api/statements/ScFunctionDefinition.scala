package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression.calculateReturns
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, StaticTraitScFunctionWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:36
*/
trait ScFunctionDefinition extends ScFunction with ScControlFlowOwner {
  def body: Option[ScExpression]

  def hasAssign: Boolean

  def assignment: Option[PsiElement]

  def removeAssignment()

  def returnUsages: Set[ScExpression] = innerReturnUsages(calculateReturns)

  def canBeTailRecursive: Boolean = getParent match {
    case (_: ScTemplateBody) && Parent(Parent(owner: ScTypeDefinition)) =>
      val ownerModifiers = owner.getModifierList
      val methodModifiers = getModifierList
      owner.isInstanceOf[ScObject] ||
        ownerModifiers.has(ScalaTokenTypes.kFINAL) ||
        methodModifiers.has(ScalaTokenTypes.kPRIVATE) ||
        methodModifiers.has(ScalaTokenTypes.kFINAL)
    case _ => true
  }

  def hasTailRecursionAnnotation: Boolean =
    annotations.map(_.typeElement)
      .flatMap(_.`type`().toOption)
      .map(_.canonicalText)
      .contains("_root_.scala.annotation.tailrec")

  def recursiveReferences: Seq[RecursiveReference] = {
    def quickCheck(ref: ScReferenceElement): Boolean = {
      ref match {
        case _: ScStableCodeReferenceElement  =>
          ref.getParent match {
            case ChildOf(_: ScConstructor) =>
              this.isConstructor && containingClass.name == ref.refName
            case cp: ScConstructorPattern if cp.ref == ref =>
              this.name == "unapply" || this.name == "unapplySeq"
            case inf: ScInfixPattern if inf.operation == ref =>
              this.name == "unapply" || this.name == "unapplySeq"
            case _ => false
          }
        case _: ScReferenceExpression =>
          if (this.name == "apply")
            if (this.containingClass.isInstanceOf[ScObject]) this.containingClass.name == ref.refName
            else true
          else this.name == ref.refName
        case _ => false
      }
    }

    val recursiveReferences = body.toSeq
      .flatMap(_.depthFirst())
      .collect {
        case element: ScReferenceElement if quickCheck(element) => element
      }.filter(_.isReferenceTo(this))

    val expressions: Set[PsiElement] = recursiveReferences match {
      case Seq() => Set.empty
      case _ =>
        def calculateExpandedReturns(expression: ScExpression): Set[ScExpression] = {
          val visitor = new ScExpression.ReturnsVisitor {

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
          case ScIfStmt(_, Some(thenBranch), None) => calculateReturns(thenBranch).flatMap(expandIf)
          case _ => Set.empty
        }) ++ Set(expression)

        innerReturnUsages(calculateExpandedReturns).flatMap(expandIf)
    }

    @scala.annotation.tailrec
    def possiblyTailRecursiveCallFor(element: PsiElement): PsiElement = element.getParent match {
      case call@(_: ScMethodCall |
                 _: ScGenericCall) => possiblyTailRecursiveCallFor(call)
      case infix: ScInfixExpr if infix.operation == element => possiblyTailRecursiveCallFor(infix)
      case statement: ScReturnStmt => statement
      case _ => element
    }

    recursiveReferences.map { reference =>
      RecursiveReference(reference, expressions(possiblyTailRecursiveCallFor(reference)))
    }
  }

  def recursionType: RecursionType = recursiveReferences match {
    case Seq() => RecursionType.NoRecursion
    case seq if seq.forall(_.isTailCall) => RecursionType.TailRecursion
    case _ => RecursionType.OrdinaryRecursion
  }

  override def controlFlowScope: Option[ScalaPsiElement] = body

  @Cached(ModCount.getBlockModificationCount, this)
  def getStaticTraitFunctionWrapper(cClass: PsiClassWrapper): StaticTraitScFunctionWrapper = {
    new StaticTraitScFunctionWrapper(this, cClass)
  }

  private def innerReturnUsages(calculateReturns: ScExpression => Set[ScExpression]): Set[ScExpression] = {
    def returnsIn(expression: ScExpression) =
      expression.depthFirst(!_.isInstanceOf[ScFunction]).collect {
        case statement: ScReturnStmt => statement
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
}

case class RecursiveReference(element: ScReferenceElement, isTailCall: Boolean)

trait RecursionType

object RecursionType {
  case object NoRecursion extends RecursionType
  case object OrdinaryRecursion extends RecursionType
  case object TailRecursion extends RecursionType
}