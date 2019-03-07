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
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.annotation.tailrec

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  *         Time: 9:49:36
  */
trait ScFunctionDefinition extends ScFunction with ScControlFlowOwner {

  import ScFunctionDefinition._

  def body: Option[ScExpression]

  def hasAssign: Boolean

  def assignment: Option[PsiElement]

  def returnUsages: Set[ScExpression] = body match {
    case Some(bodyExpression) => innerReturnUsages(bodyExpression)
    case _ => Set.empty
  }

  override def controlFlowScope: Option[ScalaPsiElement] = body

  @Cached(ModCount.getBlockModificationCount, this)
  def getStaticTraitFunctionWrapper(cClass: PsiClassWrapper): StaticTraitScFunctionWrapper =
    new StaticTraitScFunctionWrapper(this, cClass)
}

object ScFunctionDefinition {

  final case class RecursiveReferences(tailRecursive: Seq[ScReference] = Seq.empty,
                                       ordinaryRecursive: Seq[ScReference] = Seq.empty) {

    def noRecursion: Boolean = tailRecursive.isEmpty && ordinaryRecursive.isEmpty

    def tailRecursionOnly: Boolean = tailRecursive.nonEmpty && ordinaryRecursive.isEmpty
  }

  object withBody {
    def unapply(fun: ScFunctionDefinition): Option[ScExpression] = Option(fun).flatMap(_.body)
  }

  import ScExpression._

  implicit class FunctionDefinitionExt(private val function: ScFunctionDefinition) extends AnyVal {

    def recursiveReferences: Seq[ScReference] = function.body match {
      case Some(bodyExpression) =>
        bodyExpression.depthFirst().collect {
          case reference: ScReference if quickCheck(reference) &&
            reference.isReferenceTo(function) => reference
        }.toSeq
      case _ => Seq.empty
    }

    def recursiveReferencesGrouped: RecursiveReferences = recursiveReferences match {
      case Seq() => RecursiveReferences()
      case references =>
        val expressions: Set[PsiElement] = innerReturnUsages(function.body.get, createVisitor)
          .flatMap(expandIf)

        val (tailRecursive, ordinaryRecursive) = references.partition { reference =>
          expressions(possiblyTailRecursiveCallFor(reference))
        }
        RecursiveReferences(tailRecursive, ordinaryRecursive)
    }

    private def quickCheck(reference: ScReference): Boolean = {
      import ScFunction._
      val refName = reference.refName
      reference match {
        case _: ScStableCodeReference =>
          reference.getParent match {
            case ChildOf(_: ScConstructorInvocation) =>
              function.isConstructor && function.containingClass.name == refName
            case ScConstructorPattern(`reference`, _) |
                 ScInfixPattern(_, `reference`, _) =>
              function.isUnapplyMethod
            case _ => false
          }
        case _: ScReferenceExpression if function.isApplyMethod =>
          function.containingClass match {
            case scObject: ScObject => scObject.name == refName
            case _ => true
          }
        case _: ScReferenceExpression => function.name == refName
        case _ => false
      }
    }

    private def createVisitor: ReturnsVisitor = new ReturnsVisitor {

      private val booleanInstance = function.projectContext.stdTypes.Boolean

      override def visitInfixExpression(infix: ScInfixExpr): Unit = infix match {
        case ScInfixExpr(Typeable(`booleanInstance`), ElementText("&&" | "||"), right@Typeable(`booleanInstance`)) =>
          acceptVisitor(right)
        case _ => super.visitInfixExpression(infix)
      }
    }
  }

  @tailrec
  private def possiblyTailRecursiveCallFor(element: PsiElement): PsiElement = element.getParent match {
    case expression@(_: ScMethodCall |
                     _: ScGenericCall |
                     ScInfixExpr(_, `element`, _)) => possiblyTailRecursiveCallFor(expression)
    case statement: ScReturn => statement
    case _ => element
  }

  private def innerReturnUsages(expression: ScExpression,
                                visitor: ReturnsVisitor = new ReturnsVisitor): Set[ScExpression] = {
    val statements = expression.depthFirst(!_.isInstanceOf[ScFunction]).collect {
      case statement: ScReturn => statement
    }.toSet

    expression.accept(visitor)

    val file = expression.getContainingFile
    (statements ++ visitor.result).filter(_.getContainingFile == file)
  }

  private def expandIf(expression: ScExpression): Set[ScExpression] = (expression match {
    case ScIf(_, Some(thenBranch), None) => calculateReturns(thenBranch).flatMap(expandIf)
    case _ => Set.empty
  }) ++ Set(expression)
}