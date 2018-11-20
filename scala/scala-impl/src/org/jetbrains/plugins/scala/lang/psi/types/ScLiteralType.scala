package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.{ScSyntheticFunction, SyntheticClasses}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Singleton, TypeVisitor, ValueType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}
import org.jetbrains.plugins.scala.lang.psi.util.LiteralEvaluationUtil
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiElement}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.reflect.ClassTag

class ScLiteralType private(val literalValue: Any, val kind: ScLiteralType.Kind, val allowWiden: Boolean = true)
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

  private def valueFromKind: Any = {
    import ScLiteralType.Kind._
    kind match {
      case String => valueAs[String]
      case Symbol => valueAs[Symbol]
      case Boolean => valueAs[Boolean]
      case Int => valueAs[Int]
      case Long => valueAs[Long]
      case Float => valueAs[Float]
      case Double => valueAs[Double]
      case Char => valueAs[Char]
    }
  }
}

object ScLiteralType {

  import LiteralEvaluationUtil._

  sealed class Kind

  object Kind {

    case object Boolean extends Kind

    case object String extends Kind

    case object Symbol extends Kind

    case object Int extends Kind

    case object Long extends Kind

    case object Float extends Kind

    case object Double extends Kind

    case object Char extends Kind

  }

  private def fromValue(literalValue: Any)(implicit projectContext: ProjectContext): Option[ScLiteralType] = literalValue match {
    case _: Boolean => Some(apply(literalValue, Kind.Boolean))
    case _: String => Some(apply(literalValue, Kind.String))
    case _: Symbol => Some(apply(literalValue, Kind.Symbol))
    case _: Int => Some(apply(literalValue, Kind.Int))
    case _: Long => Some(apply(literalValue, Kind.Long))
    case _: Float => Some(apply(literalValue, Kind.Float))
    case _: Double => Some(apply(literalValue, Kind.Double))
    case _: Char => Some(apply(literalValue, Kind.Char))
    case _ => None
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
      case ScalaTokenTypes.tINTEGER => if (endsWith('l', 'L')) Long else Int //but a conversion exists to narrower types in case range fits
      case ScalaTokenTypes.tFLOAT => if (endsWith('f', 'F')) Float else Double
      case ScalaTokenTypes.tCHAR => Char
      case ScalaTokenTypes.tSYMBOL => Symbol
      case ScalaTokenTypes.tSTRING |
           ScalaTokenTypes.tWRONG_STRING |
           ScalaTokenTypes.tMULTILINE_STRING => String
      case ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE => Boolean
      case ScalaTokenTypes.tIDENTIFIER if node.getText == "-" => return kind(node.getTreeNext, element)
      case _ => return None
    }
    Some(inner)
  }

  def wideType(kind: Kind)(implicit projectContext: ProjectContext): ScType = {
    def getCachedClass(fqn: String) =
      ElementScope(projectContext).getCachedClass(fqn)
        .map(ScalaType.designator).getOrElse(api.Nothing)

    kind match {
      case Kind.Boolean => api.Boolean
      case Kind.String => getCachedClass("java.lang.String")
      case Kind.Symbol => getCachedClass("scala.Symbol")
      case Kind.Int => api.Int
      case Kind.Long => api.Long
      case Kind.Float => api.Float
      case Kind.Double => api.Double
      case Kind.Char => api.Char
    }
  }

  def printValue(lt: ScLiteralType): String = lt.kind match {
    case Kind.String => quoted(lt.valueAs[String])
    case Kind.Char => s"\'${lt.literalValue}\'"
    case Kind.Long => lt.literalValue.toString + "L"
    case Kind.Float => lt.literalValue.toString + "f"
    case Kind.Boolean |
         Kind.Int |
         Kind.Symbol |
         Kind.Double => lt.literalValue.toString
  }

  private def quoted(s: String): String = "\"" + StringEscapeUtils.escapeJava(s) + "\""

  private def isInteger(kind: Kind) = kind match {
    case Kind.Int | Kind.Long | Kind.Char => true
    case _ => false
  }

  def isNumeric(kind: Kind) =
    isInteger(kind) || kind == Kind.Float || kind == Kind.Double

  def widenRecursive(aType: ScType): ScType = {

    def isSingleton(param: ScTypeParam) = param.upperBound.exists(_.conforms(Singleton(param.projectContext)))

    def widenRecursiveInner(aType: ScType, visited: Set[ScParameterizedType]): ScType = aType.recursiveUpdate {
      case lit: ScLiteralType => ReplaceWith(lit.widen)
      case p: ScParameterizedType if visited(p) => Stop
      case p: ScParameterizedType =>
        p.designator match {
          case ScDesignatorType(des) => des match {
            case typeDef: ScTypeDefinition =>
              val newDesignator = widenRecursiveInner(p.designator, visited + p)
              val newArgs = (typeDef.typeParameters zip p.typeArguments).map {
                case (param, arg) if isSingleton(param) => arg
                case (_, arg) => widenRecursiveInner(arg, visited + p)
              }
              val newDes = ScParameterizedType(newDesignator, newArgs)
              ReplaceWith(newDes)
            case _ => Stop
          }
          case _ => Stop
        }
      case _: ScCompoundType => Stop
      case _ => ProcessSubtypes
    }

    widenRecursiveInner(aType, Set.empty)
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
          case Kind.Int => Some(ScLiteralType(-arg.valueAs[Int], kind))
          case Kind.Long => Some(ScLiteralType(-arg.valueAs[Long], kind))
          case Kind.Float => Some(ScLiteralType(-arg.valueAs[Float], kind))
          case Kind.Double => Some(ScLiteralType(-arg.valueAs[Double], kind))
          case Kind.Char => Some(ScLiteralType(-arg.valueAs[Char], kind))
          case _ => None
        }
      } else None
    } else None
  }

  def foldBinOpTypes(left: ScLiteralType, right: ScLiteralType, fun: ScSyntheticFunction): Option[ScLiteralType] = {
    val name = fun.name
    implicit val project: Project = fun.getProject
    val res = evaluateConstInfix[ScLiteralType](left.valueFromKind, right.valueFromKind, allowToStringConversion = false, name, value => fromValue(value))
    res
  }
}