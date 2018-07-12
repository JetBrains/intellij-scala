package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticFunction, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeVisitor, ValueType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiElement}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.reflect.ClassTag

class ScLiteralType private (val literalValue: Any, val kind: ScLiteralType.Kind, val allowWiden: Boolean = true)
                            (implicit val projectContext: ProjectContext) extends ValueType {

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitLiteralType(this)

  def wideType: ScType = ScLiteralType.wideType(kind)

  def blockWiden(): ScLiteralType = ScLiteralType.blockWiden(this)

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: ScLiteralType => kind == other.kind && literalValue == other.literalValue
      case _ => false
    }
  }

  override def hashCode(): Int = 31 * kind.hashCode() + literalValue.hashCode()

  private def valueAs[T: ClassTag]: T = literalValue.asInstanceOf[T]
}

object ScLiteralType {

  sealed class Kind
  object Kind {
    case object Null    extends Kind
    case object Boolean extends Kind
    case object String  extends Kind
    case object Symbol  extends Kind
    case object Int     extends Kind
    case object Long    extends Kind
    case object Float   extends Kind
    case object Double  extends Kind
    case object Char    extends Kind
  }

  def apply(literalValue: Any, kind: Kind)(implicit projectContext: ProjectContext): ScLiteralType =
    new ScLiteralType(literalValue, kind)

  private def blockWiden(lit: ScLiteralType): ScLiteralType =
    new ScLiteralType(lit.literalValue, lit.kind, false)(lit.projectContext)

  def kind(node: ASTNode, element: ScalaPsiElement): Option[Kind] = {
    import Kind._
    def endsWith(c1: Char, c2: Char) = {
      val lastChar = node.getText.lastOption
      lastChar.contains(c1) || lastChar.contains(c2)
    }

    val inner = node.getElementType match {
      case ScalaTokenTypes.kNULL                              => Null
      case ScalaTokenTypes.tINTEGER                           => if (endsWith('l', 'L')) Long else Int //but a conversion exists to narrower types in case range fits
      case ScalaTokenTypes.tFLOAT                             => if (endsWith('f', 'F')) Float else Double
      case ScalaTokenTypes.tCHAR                              => Char
      case ScalaTokenTypes.tSYMBOL                            => Symbol
      case ScalaTokenTypes.tSTRING |
           ScalaTokenTypes.tWRONG_STRING |
           ScalaTokenTypes.tMULTILINE_STRING                  => String
      case ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE     => Boolean
      case ScalaTokenTypes.tIDENTIFIER if node.getText == "-" => return kind(node.getTreeNext, element)
      case _                                                  => return None
    }
    Some(inner)
  }

  def wideType(kind: Kind)(implicit projectContext: ProjectContext): ScType = {
    def getCachedClass(fqn: String) =
      ElementScope(projectContext).getCachedClass(fqn)
        .map(ScalaType.designator).getOrElse(api.Nothing)

    kind match {
      case Kind.Boolean => api.Boolean
      case Kind.String  => getCachedClass("java.lang.String")
      case Kind.Symbol  => getCachedClass("scala.Symbol")
      case Kind.Int     => api.Int
      case Kind.Long    => api.Long
      case Kind.Float   => api.Float
      case Kind.Double  => api.Double
      case Kind.Char    => api.Char
    }
  }

  def printValue(lt: ScLiteralType): String = lt.kind match {
    case Kind.String => quoted(lt.valueAs[String])
    case Kind.Char   => s"\'${lt.literalValue}\'"
    case Kind.Long   => lt.literalValue.toString + "L"
    case Kind.Float  => lt.literalValue.toString + "f"
    case Kind.Boolean |
         Kind.Int     |
         Kind.Symbol  |
         Kind.Double => lt.literalValue.toString
  }

  private def quoted(s: String): String = "\"" + StringEscapeUtils.escapeJava(s) + "\""

  private def isInteger(kind: Kind) = kind match {
    case Kind.Int | Kind.Long | Kind.Char => true
    case _                                => false
  }

  private def isNumeric(kind: Kind) =
    isInteger(kind) || kind == Kind.Float || kind == Kind.Double

  def widenRecursive(aType: ScType): ScType = aType.recursiveUpdate{
    case lit: ScLiteralType => ReplaceWith(lit.widen)
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
    case c: ScCompoundType => Stop
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
    implicit val projectContext: ProjectContext = arg.projectContext

    val kind = arg.kind
    val name = fun.name
    if (isNumeric(kind)) {
      if (name == "unary_+") Some(arg)
      else if (name == "unary_-") {
        kind match {
          case Kind.Int    => Some(ScLiteralType(-arg.valueAs[Int],    kind))
          case Kind.Long   => Some(ScLiteralType(-arg.valueAs[Long],   kind))
          case Kind.Float  => Some(ScLiteralType(-arg.valueAs[Float],  kind))
          case Kind.Double => Some(ScLiteralType(-arg.valueAs[Double], kind))
          case Kind.Char   => Some(ScLiteralType(-arg.valueAs[Char],   kind))
          case _           => None
        }
      } else None
    } else None
  }

  def foldBinOpTypes(left: ScLiteralType, right: ScLiteralType, fun: ScSyntheticFunction): Option[ScLiteralType] = {
    val name = fun.name
    val synth = SyntheticClasses.get(fun.getProject)
    try {
      implicit val project: Project = fun.getProject

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

      val l: Kind = left.kind
      val r: Kind = right.kind
      val isBooleanOp = synth.bool_bin_ops.contains(name)
      val isArithOp = synth.numeric_arith_ops.contains(name) && isNumeric(l) && isNumeric(r)
      val isBitwiseOp = synth.bitwise_bin_ops.contains(name) && isInteger(l) && isInteger(r)

      import Kind._

      (l, r) match {
        case (String, String) if name == "+" =>
          Some(ScLiteralType(left.valueAs[String] + right.valueAs[String], String))
        case (_, _) if name == "==" || name == "!=" =>
          Some(ScLiteralType(boolAnyOp(left.literalValue, right.literalValue), Boolean))
        case (Boolean, Boolean) if isBooleanOp =>
          Some(ScLiteralType(boolBoolOp(left.valueAs[Boolean], right.valueAs[Boolean]), Boolean))
        case (Double, _) | (_, Double) if isArithOp =>
          Some(ScLiteralType(fracOp(left.valueAs[Double], right.valueAs[Double], name), Double))
        case (Float, _) | (_, Float) if isArithOp =>
          Some(ScLiteralType(fracOp(left.valueAs[Float], right.valueAs[Float], name): Float, Float))
        case (Long, _) | (_, Long) if isArithOp || isBitwiseOp =>
          Some(ScLiteralType(intOp(left.valueAs[Long], right.valueAs[Long], name): Long, Long))
        case _ if isArithOp || isBitwiseOp =>
          Some(ScLiteralType(intOp(left.valueAs[Int], right.valueAs[Int], name): Int, Int))
        case _ => None
      }
    } catch {
      case e: Exception =>
        println("Some exception: " + e)
        None
    }
  }
}