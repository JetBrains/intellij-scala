package org.jetbrains.plugins.scala.lang.exprTree

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScArgumentExprList, ScAssignment, ScExpression, ScFunctionExpr, ScGenericCall, ScInfixExpr, ScMethodCall, ScParenthesisedExpr, ScPostfixExpr, ScPrefixExpr, ScReferenceExpression, ScTuple, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.types.result.Failure

import scala.annotation.tailrec

class ExprTreeBuilder(rootExpr: PsiElement) {
  private implicit val elementScope: ElementScope = ElementScope(rootExpr)

  private var currentUnderscoresReversed = List.empty[UnderscoreInfo]

  private def newUnderscoreInfo(underscore: ScUnderscoreSection): UnderscoreInfo = {
    val i = currentUnderscoresReversed.size
    val info = UnderscoreInfo(underscore, i)
    currentUnderscoresReversed ::= info
    info
  }

  @tailrec
  private def buildWithUnderscoreBounds(expr: ScExpression, hasParent: Boolean = true): ExprTree = expr match {
    case _: ScUnderscoreSection if hasParent => build(expr)
    case ScParenthesisedExpr(inner) => buildWithUnderscoreBounds(inner, hasParent)
    case _ =>
      val oldUnderscores = currentUnderscoresReversed
      currentUnderscoresReversed = Nil
      try {
        val body = build(expr)
        if (currentUnderscoresReversed.nonEmpty) {
          val params = currentUnderscoresReversed.reverse
          FunctionLiteralExprTree.fromUnderscores(params, body)
        } else {
          body
        }
      } finally {
        currentUnderscoresReversed = oldUnderscores
      }
  }

  def build(expr: ScExpression): ExprTree = expr match {
    case interpolated: ScInterpolatedStringLiteral => ???
    case literal: ScLiteral => LiteralExprTree.fromPsi(literal)
    case ScParenthesisedExpr(inner) => build(inner)
    case underscore: ScUnderscoreSection =>
      val origin = newUnderscoreInfo(underscore)
      UnderscoreReferenceExprTree.Untyped(origin)
    case fun: ScFunctionExpr =>
      val bodyTree = useOrError(fun.result, expr)(buildWithUnderscoreBounds(_))
      FunctionLiteralExprTree.fromPsi(fun, bodyTree)
    case ref: ScReferenceExpression =>
      val refName = ref.refName
      ref.qualifier match {
        case Some(qualifier) =>
          val qualifierTree = build(qualifier)
          QualifiedRefExprTree(refName, qualifierTree, QualifiedRefExprTree.Origin.PsiRefExpr(ref))
        case None =>
          UnqualifiedRefExprTree(refName, UnqualifiedRefExprTree.Origin.Psi(ref))
      }
    case prefix@ScPrefixExpr(op, operand) =>
      val qualifier = build(operand)
      QualifiedRefExprTree(op.refName, qualifier, QualifiedRefExprTree.Origin.PsiPrefixTarget(prefix))
    case postfix@ScPostfixExpr(operand, op) =>
      val qualifier = build(operand)
      QualifiedRefExprTree(op.refName, qualifier, QualifiedRefExprTree.Origin.PsiPostfixTarget(postfix))
    case invocation@(_: MethodInvocation | _: ScGenericCall) =>
      buildInvocation(invocation, null, Nil)
  }

  private def useOrError[T <: PsiElement](psi: Option[T], parent: PsiElement)(f: T => ExprTree): ExprTree = {
    psi match {
      case Some(psi) => f(psi)
      case None =>
        ErrorExprTree(
          new Failure(NlsString.force("error")),
          ErrorExprTree.Origin.ParentElement(parent)
        )
    }
  }

  @tailrec
  private def buildInvocation(target: ScExpression,
                              @Nullable parentOrigin: CallExprTree.Origin,
                              parentArgLists: List[CallExprTree.ArgList]): ExprTree = {
    target match {
      case call: ScMethodCall =>
        buildInvocation(
          call.getEffectiveInvokedExpr,
          CallExprTree.Origin.PsiMethodCall(call),
          buildArgList(call.args) :: parentArgLists
        )
      case infix@ScInfixExpr.raw(left, op, right) =>
        @tailrec
        def extractArgs(expr: ScExpression, parensAround: Int = 0): Seq[CallExprTree.Arg] =
          expr match {
            case ScParenthesisedExpr(inner) =>
              extractArgs(inner, parensAround + 1)
            case ScTuple(args) =>
              buildArgs(args, hasUnderscoreBounds = parensAround > 0, allowNamed = parensAround == 0)
            case _ =>
              buildArgs(Seq(expr), hasUnderscoreBounds = parensAround > 0, allowNamed = parensAround <= 1)
          }

        val argListsAfterRight = {
          val args = extractArgs(right)
          val argListOrigin = CallExprTree.ArgList.Values.Origin.RightOfInfix(infix)
          CallExprTree.ArgList.Values(args, isUsing = false, argListOrigin) :: parentArgLists
        }

        val (ref, argListsAfterOp) = op match {
          case ref: ScReferenceExpression =>
            (ref, argListsAfterRight)
          case gen: ScGenericCall =>
            (gen.referencedExpr.asInstanceOf[ScReferenceExpression], buildTypeArgList(gen) :: argListsAfterRight)

        }
        val refName = ref.refName
        if (refName == "apply") {
          buildInvocation(left, CallExprTree.Origin.PsiInfixApply(infix), argListsAfterOp)
        } else {
          val leftTree = buildWithUnderscoreBounds(left)
          val qualRefOrigin = QualifiedRefExprTree.Origin.PsiInfixTarget(infix)
          val targetTree = QualifiedRefExprTree(ref.refName, leftTree, qualRefOrigin)
          CallExprTree(targetTree, argListsAfterOp, CallExprTree.Origin.PsiInfix(infix))
        }
      case gen: ScGenericCall =>
        buildInvocation(
          gen.referencedExpr,
          CallExprTree.Origin.PsiGenericCall(gen),
          buildTypeArgList(gen) :: parentArgLists
        )
      case expr@ScPostfixExpr(operand, ScReferenceExpression.refName("apply")) =>
        buildInvocation(operand, CallExprTree.Origin.PsiPostfixApply(expr), parentArgLists)
      case ref@ScReferenceExpression.qualified(qualifier, "apply") =>
        buildInvocation(qualifier, CallExprTree.Origin.PsiReferenceApply(ref), parentArgLists)
      case ScParenthesisedExpr(inner) =>
        buildInvocation(inner, parentOrigin, parentArgLists)
      case target =>
        val targetTree = build(target)
        assert(
          parentOrigin != null,
          "parentOrigin being null means that the initial call gave a target that was not matched until here"
        )
        CallExprTree(targetTree, parentArgLists, parentOrigin)
    }
  }

  private def buildArgList(argList: ScArgumentExprList): CallExprTree.ArgList = {
    val origin = CallExprTree.ArgList.Values.Origin.PsiArgList(argList)
    CallExprTree.ArgList.Values(buildArgs(argList.exprs, hasUnderscoreBounds = true, allowNamed = true), argList.isUsing, origin)
  }

  private def buildArgs(args: Seq[ScExpression], hasUnderscoreBounds: Boolean, allowNamed: Boolean): Seq[CallExprTree.Arg] = {
    val build: ScExpression => ExprTree =
      if (hasUnderscoreBounds) buildWithUnderscoreBounds(_) else this.build
    args.map {
      case assign@ScAssignment(ref: ScReferenceExpression, arg) if allowNamed && !ref.isQualified =>
        val argTree = useOrError(arg, assign)(build)
        CallExprTree.Arg.Named(ref.refName, argTree, assign)
      case expr =>
        CallExprTree.Arg.Positional(build(expr))
    }
  }

  private def buildTypeArgList(gen: ScGenericCall): CallExprTree.ArgList.Types = {
    val types = gen.arguments.map(_.`type`())
    val origin = CallExprTree.ArgList.Types.Origin.Psi(gen)
    CallExprTree.ArgList.Types(types, origin)
  }

}

object ExprTreeBuilder {
  def build(expr: ScExpression): ExprTree = {
    val builder = new ExprTreeBuilder(expr)
    builder.buildWithUnderscoreBounds(expr, hasParent = false)
  }
}