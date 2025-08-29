package org.jetbrains.plugins.scala.lang.exprTree

import com.intellij.psi.{PsiElement, PsiErrorElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFunctionExpr, ScReferenceExpression, ScUnderscoreSection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}
import org.jetbrains.plugins.scala.project.ProjectContext


sealed abstract class ExprTreeOrigin

sealed trait PsiElementExprTreeOrigin extends ExprTreeOrigin {
  type Psi <: PsiElement
  def psiElement: Psi
}

sealed abstract class ExprTree {
  type Origin <: ExprTreeOrigin
  def origin: Origin
}

sealed trait TypedExprTree extends ExprTree {
  def `type`: TypeResult
}

case class FunctionLiteralExprTree(params: Seq[FunctionLiteralExprTree.Param],
                                   body: ExprTree,
                                   override val origin: FunctionLiteralExprTree.Origin) extends ExprTree {
  override type Origin = FunctionLiteralExprTree.Origin
}

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
    final case class Psi(override val psiElement: ScFunctionExpr) extends Origin with PsiElementExprTreeOrigin {
      type Psi = ScFunctionExpr
    }
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
  override type Psi = ScUnderscoreSection
  override def psiElement: ScUnderscoreSection = underscore
}

sealed abstract class UnderscoreReferenceExprTree extends ExprTree {
  override type Origin = UnderscoreInfo
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
  override type Origin = LiteralExprTree.Origin
  override def `type`: TypeResult = Right(literalType)
}

object LiteralExprTree {
  def fromPsi(psiElement: ScLiteral): LiteralExprTree =
    LiteralExprTree(psiElement.literalType, Origin.Psi(psiElement))

  sealed abstract class Origin extends ExprTreeOrigin
  object Origin {
    final case class Psi(override val psiElement: ScLiteral) extends Origin with PsiElementExprTreeOrigin {
      override type Psi = ScLiteral
    }
  }
}

final case class QualifiedRefExprTree(refName: String, qualifier: ExprTree, override val origin: QualifiedRefExprTree.Origin) extends ExprTree {
  override type Origin = QualifiedRefExprTree.Origin
}

object QualifiedRefExprTree {
  sealed abstract class Origin extends PsiElementExprTreeOrigin
  object Origin {
    final case class Psi(override val psiElement: ScReferenceExpression) extends Origin {
      override type Psi = ScReferenceExpression
    }
  }
}

final case class UnqualifiedRefExprTree(refName: String, override val origin: UnqualifiedRefExprTree.Origin) extends ExprTree {
  override type Origin = UnqualifiedRefExprTree.Origin
}

object UnqualifiedRefExprTree {
  sealed abstract class Origin extends PsiElementExprTreeOrigin
  object Origin {
    final case class Psi(override val psiElement: ScReferenceExpression) extends Origin {
      override type Psi = ScReferenceExpression
    }
  }
}

case class ErrorExprTree(typeFailure: Failure, override val origin: ErrorExprTree.Origin) extends ExprTree with TypedExprTree {
  override type Origin = ErrorExprTree.Origin

  override def `type`: TypeResult = Left(typeFailure)
}

object ErrorExprTree {
  sealed trait Origin extends PsiElementExprTreeOrigin
  object Origin {
    final case class ParentElement(override val psiElement: PsiElement) extends Origin {
      override type Psi = PsiElement
    }
    final case class ErrorPsi(override val psiElement: PsiErrorElement) extends Origin {
      override type Psi = PsiErrorElement
    }
  }
}