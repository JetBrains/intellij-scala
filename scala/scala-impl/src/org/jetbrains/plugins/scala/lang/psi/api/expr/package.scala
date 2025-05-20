package org.jetbrains.plugins.scala.lang.psi.api
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScFloatingPointLiteral.FloatingPointParseResult
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScDoubleLiteral, ScIntegerLiteral}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{StdType, StdTypes, ValType}
import org.jetbrains.plugins.scala.project.ProjectContext

package object expr {
  // numeric widening
  def isNumericWidening(valueType: ScType, expected:  ScType)
                       (implicit project: ProjectContext): Boolean = {
    (getStdType(valueType) zip getStdType(expected))
      .exists { case (from, to) => project.stdTypes.canWiden(from, to) }
  }

  def numericWideningOrNarrowing(
    valType:  ScType,
    expected: ScType,
    expr:     ScExpression
  )(implicit
    project: ProjectContext
  ): ScType = {
    val narrowing = isNumericNarrowing(expr, expected)
    if (narrowing.isDefined)
      narrowing.get
    else if (isNumericWidening(valType, expected))
      expected
    else
      valType
  }

  // numeric literal narrowing
  def isNumericNarrowing(
    expr:     ScExpression,
    expected: ScType
  )(implicit
    ctx: ProjectContext
  ): Option[ScType] = {
    sealed abstract class NumLit
    final case class IntLit(value: Int) extends NumLit
    final case class DoubleLit(lit: ScDoubleLiteral) extends NumLit

    def isByte(v: Long) = v >= scala.Byte.MinValue && v <= scala.Byte.MaxValue
    def isChar(v: Long) = v >= scala.Char.MinValue && v <= scala.Char.MaxValue
    def isShort(v: Long) = v >= scala.Short.MinValue && v <= scala.Short.MaxValue

    def findLit(expr: ScExpression): Option[NumLit] =
      expr match {
        case ScIntegerLiteral(value) => Some(IntLit(value))
        case lit: ScDoubleLiteral => Some(DoubleLit(lit))
        case ScPrefixExpr(op, operand) if Set("+", "-").contains(op.refName) =>
          findLit(operand).map {
            case IntLit(value) if op.refName == "-" => IntLit(-value)
            case lit => lit
          }
        case ScParenthesisedExpr(inner) => findLit(inner)
        case _                          => None
      }

    val stdTypes = StdTypes.instance
    import stdTypes._

    def unaliasedExpected = expected.removeAbstracts.removeAliasDefinitions()

    findLit(expr).flatMap {
      case IntLit(intValue) =>
        unaliasedExpected match {
          case Char if isChar(intValue)   => Some(Char)
          case Byte if isByte(intValue)   => Some(Byte)
          case Short if isShort(intValue) => Some(Short)
          case _                          => None
        }
      case DoubleLit(lit) if expr.isInScala3File &&
          unaliasedExpected == Float &&
          FloatingPointParseResult.parseFloat(lit.getText) == FloatingPointParseResult.Ok =>
        Some(Float)
      case _ =>
        None
    }
  }

  private def getStdType(
    t: ScType
  )(implicit
    project: ProjectContext
  ): Option[StdType] = {
    val stdTypes  = project.stdTypes
    val dealiased = t.widenIfLiteral.removeAliasDefinitions()
    import stdTypes._

    dealiased match {
      case AnyVal                           => Some(AnyVal)
      case valType: ValType                 => Some(valType)
      case designatorType: ScDesignatorType => designatorType.getValType
      case _                                => None
    }
  }
}
