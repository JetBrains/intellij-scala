package org.jetbrains.plugins.scala.lang.psi.util

import com.intellij.openapi.diagnostic.ControlFlowException
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses

object LiteralEvaluationUtil {

  private trait IntegralFoldOps[T] {
    def and(a: T, b: T): T

    def or(a: T, b: T): T

    def xor(a: T, b: T): T

    def leftShift(a: T, b: Int): T

    def rightShift(a: T, b: Int): T

    def logicalRightShift(a: T, b: Int): T
  }

  private implicit object IntFoldOps extends IntegralFoldOps[Int] {
    override def and(a: Int, b: Int): Int = a & b

    override def or(a: Int, b: Int): Int = a | b

    override def xor(a: Int, b: Int): Int = a ^ b

    override def leftShift(a: Int, b: Int): Int = a << b

    override def rightShift(a: Int, b: Int): Int = a >> b

    override def logicalRightShift(a: Int, b: Int): Int = a >>> b
  }

  private implicit object LongFoldOps extends IntegralFoldOps[Long] {
    override def and(a: Long, b: Long): Long = a & b

    override def or(a: Long, b: Long): Long = a | b

    override def xor(a: Long, b: Long): Long = a ^ b

    override def leftShift(a: Long, b: Int): Long = a << b

    override def rightShift(a: Long, b: Int): Long = a >> b

    override def logicalRightShift(a: Long, b: Int): Long = a >>> b
  }

  private def fracOp[T](lv: T, rv: T, name: String)(implicit n: Fractional[T]): T = {
    name match {
      case "+" => n.plus(lv, rv)
      case "-" => n.minus(lv, rv)
      case "*" => n.times(lv, rv)
      case "/" => n.div(lv, rv)
      case _ => throw new RuntimeException(s"Unexpected fractional operator $name while folding literal types")
    }
  }

  private def intOp[T](lv: T, rv: T, name: String)(implicit n: Integral[T], d: IntegralFoldOps[T]): T = {
    name match {
      case "+" => n.plus(lv, rv)
      case "-" => n.minus(lv, rv)
      case "*" => n.times(lv, rv)
      case "/" => n.quot(lv, rv)
      case "%" => n.rem(lv, rv)
      case "&" => d.and(lv, rv)
      case "|" => d.or(lv, rv)
      case "^" => d.xor(lv, rv)
      case _ => throw new RuntimeException(s"Unexpected integral operator $name while folding literal types")
    }
  }

  private def shiftOp[T](lv: T, rv: Int, name: String)(implicit d: IntegralFoldOps[T]): T = {
    name match {
      case "<<" => d.leftShift(lv, rv)
      case ">>" => d.rightShift(lv, rv)
      case ">>>" => d.logicalRightShift(lv, rv)
      case _ => throw new RuntimeException(s"Unexpected bitwise shift operator $name while folding literal types")
    }
  }

  private def numericComp[T](lv: T, rv: T, name: String)(implicit n: Ordering[T]): Boolean = {
    name match {
      case "==" => lv == rv
      case "!=" => lv != rv
      case "<" => n.lt(lv, rv)
      case "<=" => n.lteq(lv, rv)
      case ">" => n.gt(lv, rv)
      case ">=" => n.gteq(lv, rv)
      case _ => throw new RuntimeException(s"Unexpected numeric comparison operator $name while folding literal types")
    }
  }

  private def boolBoolOp(lv: Boolean, rv: Boolean, name: String): Boolean = {
    name match {
      case "&&" => lv && rv
      case "||" => lv || rv
      case "&" => lv & rv
      case "|" => lv | rv
      case "^" => lv ^ rv
      case _ => throw new RuntimeException(s"Unexpected boolean operator $name while folding literal types")
    }
  }

  private def promoteToDouble(value: Any): Double = {
    value match {
      case d: Double => d
      case f: Float => f.toDouble
      case i: Int => i.toDouble
      case l: Long => l.toDouble
      case s: Short => s.toDouble
      case b: Byte => b.toDouble
      case c: Char => c.toDouble
      case _ => throw new RuntimeException(s"Can not promote $value to Double")
    }
  }

  private def promoteToFloat(value: Any): Float = {
    value match {
      case f: Float => f
      case i: Int => i.toFloat
      case l: Long => l.toFloat
      case s: Short => s.toFloat
      case b: Byte => b.toFloat
      case c: Char => c.toFloat
      case _ => throw new RuntimeException(s"Can not promote $value to Float")
    }
  }

  def evaluateConstInfix(left: Any, right: Any,
                         name: String,
                         allowToStringConversion: Boolean = false): Any = {
    import SyntheticClasses._
    val isNumericComp = numeric_comp_ops.contains(name)
    val isArithOp = numeric_arith_ops.contains(name)
    val isBitwiseOp = bitwise_bin_ops.contains(name)
    val isBitwiseShiftOp = bitwise_shift_ops.contains(name)

    try {
      (left, right) match {
        case (l: String, r: String) if name == "+" =>
          l + r
        case (l: String, _) if allowToStringConversion && name == "+" =>
          l + right.toString
        case (_, r: String) if allowToStringConversion && name == "+" =>
          left.toString + r
        case (_, _) if name == "==" =>
          left == right
        case (_, _) if name == "!=" =>
          left != right
        case (l: Boolean, r: Boolean) if bool_bin_ops.contains(name) =>
          boolBoolOp(l, r, name)
        case (_: Symbol, _) |
             (_, _: Symbol) |
             (_: Boolean, _) |
             (_, _: Boolean) |
             (_: String, _) |
             (_, _: String) => null
        case (_: Double, _) |
             (_, _: Double) if isNumericComp =>
          numericComp(promoteToDouble(left), promoteToDouble(right), name)
        case (_: Double, _) |
             (_, _: Double) if isArithOp =>
          fracOp(promoteToDouble(left), promoteToDouble(right), name)
        case (_: Float, _) |
             (_, _: Float) if isNumericComp =>
          numericComp(promoteToFloat(left), promoteToFloat(right), name)
        case (_: Float, _) |
             (_, _: Float) if isArithOp =>
          fracOp(promoteToFloat(left), promoteToFloat(right), name)
        case (_: Long, _) |
             (_, _: Long) if isArithOp || isBitwiseOp =>
          intOp(left.asInstanceOf[Long], right.asInstanceOf[Long], name)
        case (_, r: Long) if isArithOp || isBitwiseOp =>
          intOp(left.asInstanceOf[Long], r, name)
        case (l: Long, _) if isBitwiseShiftOp =>
          shiftOp(l, right.asInstanceOf[Int], name)
        case (_, r: Long) if isBitwiseShiftOp =>
          shiftOp(left.asInstanceOf[Int], r.toInt, name)
        case (_: Long, _) |
             (_, _: Long) if isNumericComp =>
          numericComp(left.asInstanceOf[Long], right.asInstanceOf[Long], name)
        case _ if isArithOp || isBitwiseOp =>
          intOp(left.asInstanceOf[Int], right.asInstanceOf[Int], name)
        case _ if isBitwiseShiftOp =>
          shiftOp(left.asInstanceOf[Int], right.asInstanceOf[Int], name)
        case _ if isNumericComp =>
          numericComp(left.asInstanceOf[Int], right.asInstanceOf[Int], name)
        case _ => null
      }
    } catch {
      case c: ControlFlowException => throw c
      case _: Exception => null
    }
  }

  def evaluateConstPrefix(argument: Any, name: String): Option[Any] = {
    argument match {
      case b: Byte if name == "-" => Some(-b)
      case b: Byte if name == "+" => Some(b)
      case s: Short if name == "-" => Some(-s)
      case s: Short if name == "+" => Some(s)
      case i: Int if name == "-" => Some(-i)
      case i: Int if name == "+" => Some(i)
      case l: Long if name == "-" => Some(-l)
      case l: Long if name == "+" => Some(l)
      case f: Float if name == "-" => Some(-f)
      case f: Float if name == "+" => Some(f)
      case d: Double if name == "-" => Some(-d)
      case d: Double if name == "+" => Some(d)
      case _ => None
    }
  }
}