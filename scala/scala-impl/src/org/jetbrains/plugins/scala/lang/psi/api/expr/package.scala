package org.jetbrains.plugins.scala.lang.psi.api
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScIntegerLiteral
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{StdType, StdTypes, ValType}
import org.jetbrains.plugins.scala.project.ProjectContext

package object expr {
  // numeric widening
  def isNumericWidening(
    valueType: ScType,
    expected:  ScType
  )(implicit
    project: ProjectContext
  ): Boolean = {
    val (l, r) =
      (getStdType(valueType), getStdType(expected)) match {
        case (Some(left), Some(right)) => (left, right)
        case _                         => return false
      }

    val stdTypes = project.stdTypes
    import stdTypes._

    (l, r) match {
      case (Byte, Short | Int | Long | Float | Double) => true
      case (Short, Int | Long | Float | Double)        => true
      case (Char, Int | Long | Float | Double)         => true
      case (Int, Long | Float | Double)                => true
      case (Long, Float | Double)                      => true
      case (Float, Double)                             => true
      case _                                           => false
    }
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

    def isByte(v: Long) = v >= scala.Byte.MinValue && v <= scala.Byte.MaxValue

    def isChar(v: Long) = v >= scala.Char.MinValue && v <= scala.Char.MaxValue

    def isShort(v: Long) = v >= scala.Short.MinValue && v <= scala.Short.MaxValue

    def findIntLiteralValue(expr: ScExpression): Option[Int] =
      expr match {
        case ScIntegerLiteral(value) => Some(value)
        case ScPrefixExpr(op, operand) if Set("+", "-").contains(op.refName) =>
          findIntLiteralValue(operand).map(
            v =>
              if (op.refName == "-")
                -v
              else
                v
          )
        case ScParenthesisedExpr(inner) => findIntLiteralValue(inner)
        case _                          => None
      }

    val intLiteralValue: Int =
      findIntLiteralValue(expr) match {
        case Some(value) => value
        case _           => return None
      }

    val stdTypes = StdTypes.instance
    import stdTypes._

    expected.removeAbstracts.removeAliasDefinitions() match {
      case Char if isChar(intLiteralValue)   => Option(Char)
      case Byte if isByte(intLiteralValue)   => Option(Byte)
      case Short if isShort(intLiteralValue) => Option(Short)
      case _                                 => None
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
