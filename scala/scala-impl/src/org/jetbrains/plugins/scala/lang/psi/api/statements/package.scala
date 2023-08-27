package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ElementText, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScInfixPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructorInvocation, ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.annotation.tailrec
import scala.collection.mutable

package object statements {

  final case class RecursiveReferences(tailRecursive: Seq[ScReference] = Seq.empty,
                                       ordinaryRecursive: Seq[ScReference] = Seq.empty) {
    def noRecursion: Boolean = tailRecursive.isEmpty && ordinaryRecursive.isEmpty

    def tailRecursionOnly: Boolean = tailRecursive.nonEmpty && ordinaryRecursive.isEmpty
  }

  implicit class ScFunctionExt(private val function: ScFunction) extends AnyVal {
    def parameterClausesWithExtension: Seq[ScParameterClause] =
      function.extensionMethodOwner.fold(Seq.empty[ScParameterClause])(_.effectiveParameterClauses) ++
        function.effectiveParameterClauses

    def typeParametersWithExtension: Seq[ScTypeParam] =
      function.extensionMethodOwner.fold(Seq.empty[ScTypeParam])(_.typeParameters) ++
        function.typeParameters
  }

  implicit class ScFunctionDefinitionExt(private val function: ScFunctionDefinition) extends AnyVal {
    def returnUsages: Set[ScExpression] = {
      val body = function.body match {
        case Some(value) => value
        case None => return Set.empty
      }

      val allReturnStatements: Set[ScReturn] = {
        body.depthFirst(!_.isInstanceOf[ScFunction])
          .collect { case statement: ScReturn => statement }
          .toSet
      }

      val tailExpressions: Set[ScExpression] = body.calculateTailReturns
      val file = body.getContainingFile
      (allReturnStatements ++ tailExpressions).filter(_.getContainingFile == file)
    }

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
        val returnUsages: Set[ScExpression] = function.body.map(_.calculateTailReturnsOptimizable).getOrElse(Set())
        val returnUsagesExpanded: Set[PsiElement] = returnUsages.flatMap(expandIncompleteIf)

        val (tailRecursive, ordinaryRecursive) = references.partition { reference =>
          val element = possiblyTailRecursiveCallFor(reference)
          returnUsagesExpanded.contains(element)
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

    private def expandIncompleteIf(expression: ScExpression): Set[ScExpression] = {
      val extraExpressions = expression match {
        case ScIf(_, Some(thenBranch), None) =>
          val UnitStdType = expression.getProject.stdTypes.Unit
          val canExpand = function.returnTypeElement match {
            case Some(Typeable(UnitStdType)) => true
            case _ if !function.hasAssign => true
            case _ => false
          }
          if (canExpand) {
            thenBranch.calculateTailReturnsOptimizable.flatMap(expandIncompleteIf)
          } else {
            Set.empty
          }
        case _ =>
          Set.empty
      }
      extraExpressions ++ Set(expression)
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


  implicit class ScExpressionExt(private val expr: ScExpression) extends AnyVal {
    /**
     * The method finds all child expressions (at any level) of `expr` parameter that are located at tail position
     * Note that resulting set can contain return statements that are at tail positions,
     * but can not contain the expression of the return statement
     *
     * @example body expression of method
     *          {{{
     *          def foo(x: Int): Int = {
     *            if (x == 42) return 1
     *            if (x == 23) return 2
     *            else 3
     *          }
     *          }}}
     *          contains 2 tail return expressions: `return 2` and `3`
     */
    def calculateTailReturns: Set[ScExpression] = {
      val visitor = new TailReturnsCollector
      expr.accept(visitor)
      visitor.result
    }

    /** the same as [[calculateTailReturns]] but only returns tail returns available for tail recursion optimization */
    def calculateTailReturnsOptimizable: Set[ScExpression] = {
      val visitor = new TailReturnsOptimizableCollector(expr.projectContext)
      expr.accept(visitor)
      visitor.result
    }

  }

  private[api] class TailReturnsCollector extends ScalaElementVisitor {
    private val result_ = mutable.LinkedHashSet.empty[ScExpression]

    def result: Set[ScExpression] = result_.toSet

    override def visitTry(statement: ScTry): Unit = {
      statement.expression.foreach(acceptVisitor)
      visitCatchBlock(statement.catchBlock)
    }

    protected def visitCatchBlock(catchBlock: Option[ScCatchBlock]): Unit = {
      val caseClauses = catchBlock
        .collect { case ScCatchBlock(clauses) => clauses }
        .toSeq
      caseClauses
        .flatMap(_.caseClauses)
        .flatMap(_.expr)
        .foreach(acceptVisitor)
    }

    override def visitParenthesisedExpr(expression: ScParenthesisedExpr): Unit = {
      expression.innerElement match {
        case Some(innerExpression) => acceptVisitor(innerExpression)
        case _ => super.visitParenthesisedExpr(expression)
      }
    }

    override def visitMatch(statement: ScMatch): Unit = {
      statement.expressions.foreach(acceptVisitor)
    }

    override def visitIf(statement: ScIf): Unit = {
      // if `else` expression is undefined then `if` expression can't contain returns in tail position
      statement.elseExpression match {
        case Some(elseBranch) =>
          acceptVisitor(elseBranch)
          statement.thenExpression.foreach(acceptVisitor)
        case _ =>
          super.visitIf(statement)
      }
    }

    override def visitReferenceExpression(reference: ScReferenceExpression): Unit = {
      visitExpression(reference)
    }

    override def visitExpression(expression: ScExpression): Unit = {
      val maybeLastExpression = expression match {
        case block: ScBlock => block.resultExpression
        case _ => None
      }

      maybeLastExpression match {
        case Some(lastExpression) =>
          acceptVisitor(lastExpression)
        case _ =>
          super.visitExpression(expression)
          result_ += expression
      }
    }

    protected def acceptVisitor(expression: ScExpression): Unit = {
      expression.accept(this)
    }
  }

  private class TailReturnsOptimizableCollector(project: Project) extends TailReturnsCollector {
    private val booleanInstance = project.stdTypes.Boolean

    override def visitInfixExpression(infix: ScInfixExpr): Unit = infix match {
      case ScInfixExpr(Typeable(`booleanInstance`), ElementText("&&" | "||"), right@Typeable(`booleanInstance`)) =>
        acceptVisitor(right)
      case _ => super.visitInfixExpression(infix)
    }

    override def visitTry(statement: ScTry): Unit = {
      // 1) catches are in tail position when there is no finalizer
      // 2) no calls inside a try are in tail position if there is a finalizer
      // https://github.com/scala/scala/blob/cb4719716298599408388/src/compiler/scala/tools/nsc/transform/TailCalls.scala#L382
      // https://github.com/scala/scala/blob/cb4719716298599408388/src/compiler/scala/tools/nsc/transform/TailCalls.scala#L374
      if (statement.finallyBlock.isEmpty) {
        visitCatchBlock(statement.catchBlock)
      }
    }
  }
}
