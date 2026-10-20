package org.jetbrains.plugins.scala.lang.psi.types.intrinsics

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}

import java.util.regex.PatternSyntaxException
import scala.annotation.switch

/** @see [[scala.compiletime.ops]] */
private object CompileTimeOpsIntrinsics {
  def stringOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
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

  def intOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
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

  def longOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
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

  def floatOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
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

  def doubleOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
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

  def booleanOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
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

  def anyOp(operator: String, operands: Seq[ScType])(implicit project: Project): Option[ScLiteralType] = operands match {
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
}
