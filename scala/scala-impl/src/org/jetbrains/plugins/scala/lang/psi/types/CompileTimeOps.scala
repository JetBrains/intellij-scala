package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStringLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.{ScBooleanLiteralImpl, ScIntegerLiteralImpl}
import org.jetbrains.plugins.scala.lang.psi.types.api.ValueType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType

import scala.annotation.switch

/** @see [[scala.compiletime.ops]] */

private object CompileTimeOps {
  def apply(designator: ScType, arguments: Seq[ScType]): Option[ValueType] = designator match {
    case ScProjectionType.withActual(alias: ScTypeAliasDeclaration, _) =>
      implicit def project: Project = designator.projectContext.project

      val containingClassName = Option(alias.containingClass).map(_.qualifiedName).orNull

      lazy val argumentsDealiased = arguments.map {
        case AliasType(_, _, Right(right)) => right
        case other => other
      }
      (containingClassName: @switch) match {
        case "scala.compiletime.ops.any" => anyOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.boolean" => booleanOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.int" => intOp(alias.name, argumentsDealiased)
        case "scala.compiletime.ops.string" => stringOp(alias.name, argumentsDealiased)
        case _ => None
      }
    case _ => None
  }

  private def stringOp(operator: String, operands: Seq[ScType])(implicit project: Project) = operands match {
    case Seq(StringValue(l), StringValue(r)) => (operator: @switch) match {
      case "+" => Some(StringValue(l + r))
      case _ => None
    }
    case _ => None
  }

  private def intOp(operator: String, operands: Seq[ScType])(implicit project: Project) = operands match {
    case Seq(IntValue(i)) => (operator: @switch) match {
      case "S" => Some(IntValue(i + 1))
      case "Abs" => Some(IntValue(i.abs))
      case "Negate" => Some(IntValue(-i))
      case "ToString" => Some(StringValue(i.toString))
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
}
