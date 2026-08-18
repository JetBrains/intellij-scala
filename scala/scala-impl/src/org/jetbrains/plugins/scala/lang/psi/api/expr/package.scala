package org.jetbrains.plugins.scala.lang.psi.api
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScFloatingPointLiteral.FloatingPointParseResult
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.{ScDoubleLiteral, ScIntegerLiteral}
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.ScIntegerLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{StdType, StdTypes, ValType}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScLiteralType, ScType}
import org.jetbrains.plugins.scala.project.ProjectContext

package object expr {
  // numeric widening
  def isNumericWidening(
    valueType: ScType,
    expected:  ScType
  )(implicit
    project: ProjectContext,
    context: Context
  ): Boolean = {
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
    implicit val context: Context = Context(expr)

    val narrowing = isNumericNarrowing(expr, valType, expected)
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
    valType:  ScType,
    expected: ScType
  )(implicit
    ctx: ProjectContext
  ): Option[ScType] = {
    implicit val context: Context = Context(expr)

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


    def doubleLitCanParseAsFloat: Boolean =
      expr.isInScala3File &&
        findLit(expr).exists {
          case DoubleLit(doubleLit) => FloatingPointParseResult.parseFloat(doubleLit.getText) == FloatingPointParseResult.Ok
          case _ => false
        }

    def longValue: Option[Long] =
      valType.removeAbstracts.removeAliasDefinitions() match {
        case ScLiteralType(ScIntegerLiteralImpl.Value(int)) =>
          Some(int.toLong)
        // If a literal type is of type Long (even 1L),
        // it actually never conforms to Char/Byte/Short/Int.
        // case ScLiteralType(ScLongLiteralImpl.Value(long)) => Some(long)
        case _ =>
          // If the type is not a literal type (for example, in Scala 2.12),
          // we also search for an explicit integer literal
          findLit(expr).collect { case IntLit(int) => int }
      }

    val stdTypes = StdTypes.instance
    import stdTypes._

    val unaliasedExpected = expected.removeAbstracts.removeAliasDefinitions()
    val fits = unaliasedExpected match {
      case Char  => longValue.exists(isChar)
      case Byte  => longValue.exists(isByte)
      case Short => longValue.exists(isShort)
      case Float => doubleLitCanParseAsFloat
      // we don't need to check for Int,
      // because only a Long literal would need to be checked,
      // but Long literals never conform to Char/Byte/Short/Int.
      case _     => false
    }

    if (fits) Some(unaliasedExpected)
    else None
  }

  private def getStdType(
    t: ScType
  )(implicit
    project: ProjectContext,
    context: Context
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
