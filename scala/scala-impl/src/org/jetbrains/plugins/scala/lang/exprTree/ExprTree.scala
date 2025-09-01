package org.jetbrains.plugins.scala.lang.exprTree

import com.intellij.psi.{PsiElement, PsiErrorElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScAssignment, ScBlock, ScBlockExpr, ScFunctionExpr, ScGenericCall, ScInfixExpr, ScMethodCall, ScPostfixExpr, ScPrefixExpr, ScReferenceExpression, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}


sealed abstract class ExprTreeOrigin

sealed trait PsiElementExprTreeOrigin extends ExprTreeOrigin {
  def psiElement: PsiElement
}

sealed abstract class ExprTree {
  def origin: ExprTreeOrigin
}

sealed trait ResolvableExprTree extends ExprTree

sealed trait ResolvedExprTree extends ResolvableExprTree {
  def resolved: PsiElement
}

sealed trait TypedExprTree extends ExprTree {
  def `type`: TypeResult
}

case class FunctionLiteralExprTree(params: Seq[FunctionLiteralExprTree.Param],
                                   body: ExprTree,
                                   override val origin: FunctionLiteralExprTree.Origin) extends ExprTree

object FunctionLiteralExprTree {
  def fromPsi(funExpr: ScFunctionExpr, body: ExprTree): FunctionLiteralExprTree = {
    val params = funExpr.parameters.map {
      param => Param.Untyped(ParamOrigin.Psi(param))
    }
    FunctionLiteralExprTree(params, body, Origin.Psi(funExpr))
  }

  def fromUnderscores(params: Seq[UnderscoreInfo], body: ExprTree): FunctionLiteralExprTree =
    FunctionLiteralExprTree(
      params.map(p => Param.Untyped(p)),
      body,
      Origin.UnderscoreSection(params)
    )

  sealed abstract class Origin extends ExprTreeOrigin
  object Origin {
    final case class Psi(override val psiElement: ScFunctionExpr) extends Origin with PsiElementExprTreeOrigin
    final case class UnderscoreSection(infos: Seq[UnderscoreInfo]) extends Origin
  }

  sealed trait ParamOrigin
  object ParamOrigin {
    case class Psi(param: ScParameter) extends ParamOrigin
    type Underscore = UnderscoreInfo
    val Underscore: UnderscoreInfo.type = UnderscoreInfo
  }

  sealed abstract class Param {
    def origin: ParamOrigin
  }
  object Param {
    sealed abstract class Typed extends Param{
      def `type`: TypeResult
    }
    final case class Untyped(override val origin: ParamOrigin) extends Param
    final case class Inferred(override val `type`: TypeResult, override val origin: ParamOrigin) extends Typed
    final case class ExplicitlyTyped(override val `type`: TypeResult, override val origin: ParamOrigin) extends Typed
  }
}

final case class UnderscoreInfo(underscore: ScUnderscoreSection, i: Int) extends PsiElementExprTreeOrigin with FunctionLiteralExprTree.ParamOrigin {
  override def psiElement: ScUnderscoreSection = underscore
}

sealed abstract class UnderscoreReferenceExprTree extends ExprTree {
  override def origin: UnderscoreInfo
  def underscore: ScUnderscoreSection
}
object UnderscoreReferenceExprTree {
  final case class Untyped(override val origin: UnderscoreInfo) extends UnderscoreReferenceExprTree {
    override def underscore: ScUnderscoreSection = origin.underscore
  }

  final case class Typed(override val `type`: TypeResult, override val origin: UnderscoreInfo) extends UnderscoreReferenceExprTree with TypedExprTree {
    override def underscore: ScUnderscoreSection = origin.underscore
  }
}

final case class LiteralExprTree(literalType: ScType, override val origin: LiteralExprTree.Origin) extends TypedExprTree {
  override def `type`: TypeResult = Right(literalType)
}
object LiteralExprTree {
  def fromPsi(psiElement: ScLiteral): LiteralExprTree =
    LiteralExprTree(psiElement.literalType, Origin.Psi(psiElement))

  sealed abstract class Origin extends ExprTreeOrigin
  object Origin {
    final case class Psi(override val psiElement: ScLiteral) extends Origin with PsiElementExprTreeOrigin
  }
}

sealed abstract class RefExprTree extends ExprTree with ResolvableExprTree

final case class QualifiedRefExprTree(refName: String, qualifier: ExprTree, override val origin: QualifiedRefExprTree.Origin) extends RefExprTree
object QualifiedRefExprTree {
  sealed abstract class Origin extends PsiElementExprTreeOrigin
  object Origin {
    final case class PsiRefExpr(override val psiElement: ScReferenceExpression) extends Origin
    final case class PsiInfixTarget(override val psiElement: ScInfixExpr) extends Origin
    final case class PsiPrefixTarget(override val psiElement: ScPrefixExpr) extends Origin
    final case class PsiPostfixTarget(override val psiElement: ScPostfixExpr) extends Origin
  }
}

final case class UnqualifiedRefExprTree(refName: String, override val origin: UnqualifiedRefExprTree.Origin) extends RefExprTree
object UnqualifiedRefExprTree {
  sealed abstract class Origin extends ExprTreeOrigin
  object Origin {
    final case class Psi(override val psiElement: ScReferenceExpression) extends Origin with PsiElementExprTreeOrigin
  }
}

final case class CallExprTree(target: ExprTree, argsLists: Seq[CallExprTree.ArgList], override val origin: CallExprTree.Origin) extends ExprTree {
  assert(argsLists.nonEmpty)
}
object CallExprTree {
  sealed abstract class ArgList {
    def origin: ArgList.Origin
  }
  object ArgList {
    sealed abstract class Origin

    final case class Types(types: Seq[TypeResult], override val origin: Types.Origin) extends ArgList
    object Types {
      sealed abstract class Origin extends ArgList.Origin
      object Origin {
        final case class Psi(genericCall: ScGenericCall) extends Origin
      }
    }

    final case class Values(args: Seq[Arg], isUsing: Boolean, override val origin: Values.Origin) extends ArgList
    object Values {
      sealed abstract class Origin extends ArgList.Origin
      object Origin {
        final case class PsiArgList(argList: ScArgumentExprList) extends Origin
        final case class RightOfInfix(infix: ScInfixExpr) extends Origin
      }
    }
  }

  sealed abstract class Arg
  object Arg {
    final case class Positional(expr: ExprTree) extends Arg
    final case class Named(name: String, expr: ExprTree, origin: ScAssignment) extends Arg
  }

  sealed abstract class Origin extends ExprTreeOrigin
  object Origin {
    final case class PsiMethodCall(override val psiElement: ScMethodCall) extends Origin with PsiElementExprTreeOrigin
    final case class PsiReferenceApply(override val psiElement: ScReferenceExpression) extends Origin with PsiElementExprTreeOrigin
    final case class PsiInfix(override val psiElement: ScInfixExpr) extends Origin with PsiElementExprTreeOrigin
    final case class PsiInfixApply(override val psiElement: ScInfixExpr) extends Origin with PsiElementExprTreeOrigin
    final case class PsiPostfixApply(override val psiElement: ScPostfixExpr) extends Origin with PsiElementExprTreeOrigin
    final case class PsiGenericCall(override val psiElement: ScGenericCall) extends Origin with PsiElementExprTreeOrigin
  }
}

final case class BlockExprTree(stmts: Seq[ExprTree], symbols: Seq[Symbol], override val origin: BlockExprTree.Origin) extends ExprTree
object BlockExprTree {

  sealed abstract class Origin extends ExprTreeOrigin
  object Origin {
    final case class Psi(override val psiElement: ScBlock) extends Origin with PsiElementExprTreeOrigin
  }
}

final case class ErrorExprTree(typeFailure: Failure, override val origin: ErrorExprTree.Origin) extends ExprTree with TypedExprTree {
  override def `type`: TypeResult = Left(typeFailure)
}
object ErrorExprTree {
  sealed trait Origin extends PsiElementExprTreeOrigin
  object Origin {
    final case class ParentElement(override val psiElement: PsiElement) extends Origin
    final case class ErrorPsi(override val psiElement: PsiErrorElement) extends Origin
  }
}