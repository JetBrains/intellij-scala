package org.jetbrains.plugins.scala.lang.exprTree

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderscoreSection
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult


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

abstract class FunctionLiteralExprTree extends ExprTree {
  def params: Seq[FunctionLiteralExprTree.Param]
  def body: ExprTree
}

object FunctionLiteralExprTree {
  abstract class WithTypedParams extends FunctionLiteralExprTree {

  }
  abstract class Typed extends WithTypedParams with TypedExprTree {
    override def body: TypedExprTree
  }

  sealed abstract class ParamOrigin
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

case class UnderscoreInfo(underscore: ScUnderscoreSection, i: Int) extends PsiElementExprTreeOrigin {
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
    case class Psi(override val psiElement: ScLiteral) extends Origin with PsiElementExprTreeOrigin {
      type Psi = ScLiteral
    }
  }
}
