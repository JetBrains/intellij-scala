package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStringLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.{ScBooleanLiteralImpl, ScCharLiteralImpl, ScDoubleLiteralImpl, ScFloatLiteralImpl, ScIntegerLiteralImpl, ScLongLiteralImpl}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType

import java.util.regex.PatternSyntaxException
import scala.annotation.switch

/** @see [[scala.compiletime.ops]] */
private object CompileTimeOps {
  def apply(designator: ScType, arguments: Seq[ScType]): Option[ScLiteralType] = designator match {
    case ScProjectionType.withActual(alias: ScTypeAliasDeclaration, _) =>
      implicit def project: Project = designator.projectContext.project

      val containingClassName = Option(alias.containingClass).map(_.qualifiedName).orNull

      //TODO: question of de-aliasing/reducing type aliases is more general and complicated
      // For example see SCL-21176 and SCL-20263)
      // For now (in Scala 3.2.1-RC4) it's done for scala.compiletime.ops
      // But ideally it should be done more uniformly.
      // See also: https://github.com/lampepfl/dotty/pull/14586
      lazy val argumentsDealiased = arguments.map(_.removeAliasDefinitions())
      (containingClassName: @switch) match {
        case "scala.compiletime.ops.any" => anyOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.boolean" => booleanOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.int" => intOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.long" => longOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.float" => floatOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.double" => doubleOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.string" => stringOp(alias.name, argumentsDealiased)
        case _ => None
      }
    case _ => None
  }

  private def stringOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
    case Seq(StringValue(l), StringValue(r)) => (operator: @switch) match {
      case "+" => Some(StringValue(l + r))
      case "Matches" =>
        //TODO: Ideally we need to cache compiled regular expressions for performance reasons (probably some bounded cache would do)"
        // But at this moment it's not clear how much `scala.compiletime.ops.string.Matches` will be used in practice
        // and whether it's worth caching, so for now leave it as is
        try Some(BooleanValue(l.matches(r))) catch {
          case _: PatternSyntaxException =>
            None
        }
      case _ => None
    }
    case Seq(StringValue(l), IntValue(r)) => (operator: @switch) match {
      case "CharAt" =>
        if (0 <= r && r < l.length) Some(CharValue(l.charAt(r)))
        else None
      case _ => None
    }
    case Seq(StringValue(s), IntValue(x), IntValue(y)) => (operator: @switch) match {
      case "Substring" =>
        if (0 <= x && x <= y && y <= s.length)
          Some(StringValue(s.substring(x, y)))
        else None
      case _ => None
    }
    case Seq(StringValue(v)) => (operator: @switch) match {
      case "Length" => Some(IntValue(v.length))
      case _ => None
    }
    case _ => None
  }

  private def intOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
    case Seq(IntValue(i)) => (operator: @switch) match {
      case "S" => Some(IntValue(i + 1))
      case "Abs" => Some(IntValue(i.abs))
      case "Negate" => Some(IntValue(-i))
      case "NumberOfLeadingZeros" => Some(IntValue(java.lang.Integer.numberOfLeadingZeros(i)))

      case "ToLong" => Some(LongValue(i.toLong))
      case "ToFloat" => Some(FloatValue(i.toFloat))
      case "ToDouble" => Some(DoubleValue(i.toDouble))
      case "ToString" => Some(StringValue(i.toString)) //NOTE: deprecated since 3.2.0
      case _ => None
    }
    case Seq(IntValue(l), IntValue(r)) => (operator: @switch) match {
      case "+" => Some(IntValue(l + r))
      case "-" => Some(IntValue(l - r))
      case "*" => Some(IntValue(l * r))
      case "/" => Some(IntValue(l / r))
      case "%" => Some(IntValue(l % r))
      case "<<" => Some(IntValue(l << r))
      case ">>" => Some(IntValue(l >> r))
      case ">>>" => Some(IntValue(l >>> r))
      case "^" => Some(IntValue(l ^ r))

      case "<" => Some(BooleanValue(l < r))
      case ">" => Some(BooleanValue(l > r))
      case ">=" => Some(BooleanValue(l >= r))
      case "<=" => Some(BooleanValue(l <= r))

      case "BitwiseAnd" => Some(IntValue(l & r))
      case "BitwiseOr" => Some(IntValue(l | r))
      case "Min" => Some(IntValue(l.min(r)))
      case "Max" => Some(IntValue(l.max(r)))
      case _ => None
    }
    case _ => None
  }

  private def longOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
    case Seq(LongValue(v)) => (operator: @switch) match {
      case "S" => Some(LongValue(v + 1))
      case "Abs" => Some(LongValue(v.abs))
      case "Negate" => Some(LongValue(-v))
      case "NumberOfLeadingZeros" => Some(IntValue(java.lang.Long.numberOfLeadingZeros(v)))

      case "ToInt" => Some(IntValue(v.toInt))
      case "ToFloat" => Some(FloatValue(v.toFloat))
      case "ToDouble" => Some(DoubleValue(v.toDouble))
      case _ => None
    }
    case Seq(LongValue(l), LongValue(r)) => (operator: @switch) match {
      case "+" => Some(LongValue(l + r))
      case "-" => Some(LongValue(l - r))
      case "*" => Some(LongValue(l * r))
      case "/" => Some(LongValue(l / r))
      case "%" => Some(LongValue(l % r))
      case "<<" => Some(LongValue(l << r))
      case ">>" => Some(LongValue(l >> r))
      case ">>>" => Some(LongValue(l >>> r))
      case "^" => Some(LongValue(l ^ r))

      case "<" => Some(BooleanValue(l < r))
      case ">" => Some(BooleanValue(l > r))
      case ">=" => Some(BooleanValue(l >= r))
      case "<=" => Some(BooleanValue(l <= r))

      case "BitwiseAnd" => Some(LongValue(l & r))
      case "BitwiseOr" => Some(LongValue(l | r))
      case "Min" => Some(LongValue(l.min(r)))
      case "Max" => Some(LongValue(l.max(r)))
      case _ => None
    }
    case _ => None
  }

  private def floatOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
    case Seq(FloatValue(v)) => (operator: @switch) match {
      case "Abs" => Some(FloatValue(v.abs))
      case "Negate" => Some(FloatValue(-v))

      case "ToInt" => Some(IntValue(v.toInt))
      case "ToLong" => Some(LongValue(v.toLong))
      case "ToDouble" => Some(DoubleValue(v.toDouble))

      case _ => None
    }
    case Seq(FloatValue(l), FloatValue(r)) => (operator: @switch) match {
      case "+" => Some(FloatValue(l + r))
      case "-" => Some(FloatValue(l - r))
      case "*" => Some(FloatValue(l * r))
      case "/" => Some(FloatValue(l / r))
      case "%" => Some(FloatValue(l % r))

      case "<" => Some(BooleanValue(l < r))
      case ">" => Some(BooleanValue(l > r))
      case ">=" => Some(BooleanValue(l >= r))
      case "<=" => Some(BooleanValue(l <= r))

      case "Min" => Some(FloatValue(l.min(r)))
      case "Max" => Some(FloatValue(l.max(r)))
      case _ => None
    }
    case _ => None
  }

  private def doubleOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
    case Seq(DoubleValue(v)) => (operator: @switch) match {
      case "Abs" => Some(DoubleValue(v.abs))
      case "Negate" => Some(DoubleValue(-v))

      case "ToInt" => Some(IntValue(v.toInt))
      case "ToLong" => Some(LongValue(v.toLong))
      case "ToFloat" => Some(FloatValue(v.toFloat))

      case _ => None
    }
    case Seq(DoubleValue(l), DoubleValue(r)) => (operator: @switch) match {
      case "+" => Some(DoubleValue(l + r))
      case "-" => Some(DoubleValue(l - r))
      case "*" => Some(DoubleValue(l * r))
      case "/" => Some(DoubleValue(l / r))
      case "%" => Some(DoubleValue(l % r))

      case "<" => Some(BooleanValue(l < r))
      case ">" => Some(BooleanValue(l > r))
      case ">=" => Some(BooleanValue(l >= r))
      case "<=" => Some(BooleanValue(l <= r))

      case "Min" => Some(DoubleValue(l.min(r)))
      case "Max" => Some(DoubleValue(l.max(r)))
      case _ => None
    }
    case _ => None
  }

  private def booleanOp(operator: String, operands: Seq[ScType])(implicit project: Project) = operands match {
    case Seq(BooleanValue(b)) => (operator: @switch) match {
      case "!" => Some(BooleanValue(!b))
      case _ => None
    }
    case Seq(BooleanValue(l), BooleanValue(r)) => (operator: @switch) match {
      case "^" => Some(BooleanValue(l ^ r))
      case "&&" => Some(BooleanValue(l && r))
      case "||" => Some(BooleanValue(l || r))
      case _ => None
    }
    case _ => None
  }

  private def anyOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
    case Seq(AnyValue(l), AnyValue(r)) => (operator: @switch) match {
      case "==" => Some(BooleanValue(l == r))
      case "!=" => Some(BooleanValue(l != r))
      case _ => None
    }
    case Seq(operand) =>
      (operator: @switch) match {
        case "IsConst" =>
          val isConst = operand.is[ScLiteralType]
          Some(BooleanValue(isConst))
        case "ToString" =>
          operand match {
            case AnyValue(value) =>
              Some(StringValue(value.toString))
            case _ => None
          }
        case _ => None
      }
    case _ => None
  }

  private object AnyValue {
    def unapply(t: ScLiteralType): Option[Any] = Some(t.value.value)
  }

  private object BooleanValue {
    def apply(v: Boolean)(implicit context: Project): ScLiteralType = ScLiteralType(ScBooleanLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[Boolean] = t.value match {
      case ScBooleanLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }

  private object IntValue {
    def apply(v: Int)(implicit context: Project): ScLiteralType = ScLiteralType(ScIntegerLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[Int] = t.value match {
      case ScIntegerLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }

  private object StringValue {
    def apply(v: String)(implicit context: Project): ScLiteralType = ScLiteralType(ScStringLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[String] = t.value match {
      case ScStringLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }

  private object CharValue {
    def apply(v: Char)(implicit context: Project): ScLiteralType = ScLiteralType(ScCharLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[Char] = t.value match {
      case ScCharLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }

  private object LongValue {
    def apply(v: Long)(implicit context: Project): ScLiteralType = ScLiteralType(ScLongLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[Long] = t.value match {
      case ScLongLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }

  private object FloatValue {
    def apply(v: Float)(implicit context: Project): ScLiteralType = ScLiteralType(ScFloatLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[Float] = t.value match {
      case ScFloatLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }

  private object DoubleValue {
    def apply(v: Double)(implicit context: Project): ScLiteralType = ScLiteralType(ScDoubleLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[Double] = t.value match {
      case ScDoubleLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }
}
