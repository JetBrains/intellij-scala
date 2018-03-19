package org.jetbrains.plugins.scala.lang.psi.types
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScLiteralTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticFunction, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeVisitor, ValueType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.project.ProjectContext

class ScLiteralType private (val literalValue: Any, private val project: ProjectContext, val wideType: ScType, val allowWiden: Boolean = true) extends ValueType {
  override def visitType(visitor: TypeVisitor): Unit = visitor.visitLiteralType(this)

  def blockWiden(): ScLiteralType = ScLiteralType.blockWiden(this)

  override implicit def projectContext: ProjectContext = project

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: ScLiteralType => other.literalValue == literalValue
      case _ => false
    }
  }

  override def hashCode(): Int = Option(literalValue).map(_.hashCode).getOrElse(0)
}

object ScLiteralType {
  import scala.collection.concurrent._
  private val cache: Map[(Any, Project, Boolean), ScLiteralType] = {
    import scala.collection.JavaConverters._
    new java.util.concurrent.ConcurrentHashMap[(Any, Project, Boolean), ScLiteralType].asScala
  }

  def getType(typeElement: ScLiteralTypeElement): TypeResult = {
    ScLiteralImpl.getLiteralType(typeElement.getLiteralNode, typeElement).map(l => apply(typeElement.getLiteral.getValue, typeElement.projectContext, l))
  }

  def apply(literalValue: Any, project: ProjectContext, wideType: ScType): ScLiteralType = {
    cache.putIfAbsent((literalValue, project, true), new ScLiteralType(literalValue, project, wideType))
    cache((literalValue, project, true))
  }

  private def blockWiden(lit: ScLiteralType): ScLiteralType = {
    cache.putIfAbsent((lit.literalValue, lit.project, false), new ScLiteralType(lit.literalValue, lit.project, lit.wideType, false))
    cache((lit.literalValue, lit.project, false))
  }

  def widen(aType: ScType): ScType = aType match {
    case lit: ScLiteralType if lit.allowWiden => lit.wideType
    case other => other
  }

  def widenRecursive(aType: ScType): ScType = aType.recursiveUpdate{
    case lit: ScLiteralType => ReplaceWith(widen(lit))
    case p: ScParameterizedType =>
      p.designator match {
        case ScDesignatorType(des) => des match {
          case typeDef: ScTypeDefinition =>
            import org.jetbrains.plugins.scala.lang.psi.types.api._
            val newDes = ScParameterizedType(widenRecursive(p.designator), (typeDef.typeParameters zip p.typeArguments).map{
              case (param, arg) if param.upperBound.exists(_.conforms(Singleton(p.projectContext))) => arg
              case (_, arg) => widenRecursive(arg)
            })
            ReplaceWith(newDes)
          case _ => Stop
        }
        case _ => Stop
      }
    case _ => ProcessSubtypes
  }

  trait IntegralFoldOps[T] {
    def and(a: T, b: T): T
    def or(a: T, b: T): T
    def xor(a: T, b: T): T
  }

  implicit object IntFoldOps extends IntegralFoldOps[Int] {
    override def and(a: Int, b: Int): Int = a & b
    override def or(a: Int, b: Int): Int = a | b
    override def xor(a: Int, b: Int): Int = a ^ b
  }

  implicit object LongFoldOps extends IntegralFoldOps[Long] {
    override def and(a: Long, b: Long): Long = a & b
    override def or(a: Long, b: Long): Long = a | b
    override def xor(a: Long, b: Long): Long = a ^ b
  }

  //TODO we have also support for Byte and Short, but that's not a big deal since literal types for them currently can't be parsed
  def foldUnOpTypes(arg: ScLiteralType, fun: ScSyntheticFunction): Option[ScLiteralType] = {
    val stdTypes = arg.projectContext.stdTypes
    import stdTypes._
    val synth = SyntheticClasses.get(fun.getProject)
    val wide = arg.wideType
    val name = fun.name
    val project = arg.project
    if (synth.numeric.exists(_.stdType eq wide)) {
      if (name == "unary_+") Some(arg)
      else if (name == "unary_-") {
        wide match {
          case Int => Some(ScLiteralType(-arg.literalValue.asInstanceOf[Int], project, wide))
          case Long => Some(ScLiteralType(-arg.literalValue.asInstanceOf[Long], project, wide))
          case Float => Some(ScLiteralType(-arg.literalValue.asInstanceOf[Float], project, wide))
          case Double => Some(ScLiteralType(-arg.literalValue.asInstanceOf[Double], project, wide))
          case Char => Some(ScLiteralType(-arg.literalValue.asInstanceOf[Char], project, wide))
          case Short => Some(ScLiteralType(-arg.literalValue.asInstanceOf[Short], project, wide))
          case Byte => Some(ScLiteralType(-arg.literalValue.asInstanceOf[Byte], project, wide))
          case _ => None
        }
      } else None
    } else None
  }

  def foldBinOpTypes(left: ScLiteralType, right: ScLiteralType, fun: ScSyntheticFunction): Option[ScLiteralType] = {
    val name = fun.name
    val synth = SyntheticClasses.get(fun.getProject)
    try {
      val project = fun.getProject

      def fracOp[T](lv: T, rv: T, name: String)(implicit n: Fractional[T]): T = {
        name match {
          case "+" => n.plus(lv, rv)
          case "-" => n.minus(lv, rv)
          case "*" => n.times(lv, rv)
          case "/" => n.div(lv, rv)
          case _ => throw new RuntimeException(s"Unexpected fractional operator $name while folding literal types")
        }
      }

      def intOp[T](lv: T, rv: T, name: String)(implicit n: Integral[T], d: IntegralFoldOps[T]): T = {
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

      def boolBoolOp(lv: Boolean, rv: Boolean): Boolean = {
        name match {
          case "&&" => lv && rv
          case "||" => lv || rv
          case "&" => lv & rv
          case "|" => lv | rv
          case "^" => lv ^ rv
          case _ => throw new RuntimeException(s"Unexpected boolean operator $name while folding literal types")
        }
      }

      def boolAnyOp(lv: Any, rv: Any): Boolean = {
        name match {
          case "==" => lv == rv
          case "!=" => lv != rv
          case _ => throw new RuntimeException(s"Unexpected boolean operator $name while folding literal types")
        }
      }

      val l: ScType = left.wideType
      val r: ScType = right.wideType
      val isBooleanOp = synth.bool_bin_ops.contains(name)
      val isArithOp = synth.numeric_arith_ops.contains(name) && synth.numeric.exists(_.stdType eq l) && synth.numeric.exists(_.stdType eq r)
      val isBitwiseOp = synth.bitwise_bin_ops.contains(name) && synth.integer.exists(_.stdType eq l) && synth.integer.exists(_.stdType eq r)
      val stdTypes = l.projectContext.stdTypes
      val stringCanonicalText = "_root_.java.lang.String"
      import stdTypes._
      (l, r) match {
        case (desl: ScDesignatorType, desr: ScDesignatorType) if desl.canonicalText == stringCanonicalText && desr.canonicalText == stringCanonicalText && name == "+" =>
          Some(ScLiteralType(left.literalValue.asInstanceOf[String] + right.literalValue.asInstanceOf[String], project, desl))
        case (_, _) if name == "==" || name == "!=" =>
          Some(ScLiteralType(boolAnyOp(left.literalValue, right.literalValue), project, Boolean))
        case (Boolean, Boolean) if isBooleanOp =>
          Some(ScLiteralType(boolBoolOp(left.literalValue.asInstanceOf[Boolean], right.literalValue.asInstanceOf[Boolean]), project, Boolean))
        case (Double, _) | (_, Double) if isArithOp =>
          Some(ScLiteralType(fracOp(left.literalValue.asInstanceOf[Double], right.literalValue.asInstanceOf[Double], name), project, Double))
        case (Float, _) | (_, Float) if isArithOp =>
          Some(ScLiteralType(fracOp(left.literalValue.asInstanceOf[Float], right.literalValue.asInstanceOf[Float], name): Float, project, Float))
        case (Long, _) | (_, Long) if isArithOp || isBitwiseOp =>
          Some(ScLiteralType(intOp(left.literalValue.asInstanceOf[Long], right.literalValue.asInstanceOf[Long], name): Long, project, Long))
        case _ if isArithOp || isBitwiseOp =>
          Some(ScLiteralType(intOp(left.literalValue.asInstanceOf[Int], right.literalValue.asInstanceOf[Int], name): Int, project, Int))
        case _ => None
      }
    } catch {
      case e: Exception =>
        println("Some exception: " + e)
        None
    }
  }
}