package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStringLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, ScLiteralType, ScParameterizedType, ScType, ScalaType, api, extractTypeParameters}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionTypeFactory.{extractMember, extractParameterizedType, extractQualifiedName}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.reflect.ClassTag

sealed trait FunctionTypeFactory[D <: ScTypeDefinition, T] {

  import FunctionTypeFactory._

  val TypeName: String

  def apply(t: T)(implicit scope: ElementScope): ValueType

  def unapply(`type`: ScType): Option[T] =
    extractParameterizedType(`type`).flatMap {
      case pTy if extractQualifiedName(pTy.designator).exists(_.startsWith(TypeName)) =>
        val args = pTy.typeArguments
        if (args.isEmpty) None
        else unapplyCollector.unapply(args)
      case _ =>
        None
    }

  protected final def apply(parameters: Seq[ScType], suffix: String)
                           (implicit scope: ElementScope, tag: ClassTag[D]): ValueType =
    scope.getCachedClass(TypeName + suffix).collect {
      case definition: D => ScParameterizedType(ScalaType.designator(definition), parameters).asInstanceOf[ValueType]
    }.getOrElse(api.Nothing)

  protected def unapplyCollector: PartialFunction[Seq[ScType], T]
}

object FunctionTypeFactory {
  @tailrec
  private[api] def extractParameterizedType(`type`: ScType, depth: Int = 100): Option[ParameterizedType] = `type` match {
    case _ if depth == 0 => None //hack for https://youtrack.jetbrains.com/issue/SCL-6880 to avoid infinite loop.
    case AliasLowerBound(lower) => extractParameterizedType(lower, depth - 1)
    case paramType: ParameterizedType => Some(paramType)
    case _ => None
  }

  private[api] def extractMember(`type`: ScType): Option[ScMember] =
    `type`
      .extractDesignated(expandAliases = true)
      .collect { case member: ScMember => member }

  private[api] def extractQualifiedName(`type`: ScType): Option[String] =
    extractMember(`type`)
      .flatMap(member => member.qualifiedNameOpt)

  private[this] object AliasLowerBound {

    def unapply(`type`: ScType): Option[ScType] = `type` match {
      case AliasType(_: ScTypeAliasDefinition, Right(lower), _) => Option(lower)
      case _                                                    => None
    }
  }
}

trait FunctionTypeBase extends FunctionTypeFactory[ScTrait, (ScType, Seq[ScType])] {
  override def apply(pair: (ScType, Seq[ScType]))(implicit scope: ElementScope): ValueType = {
    val (returnType, parameters) = pair
    apply(parameters :+ returnType, parameters.length.toString)
  }

  override protected def unapplyCollector: PartialFunction[Seq[ScType], (ScType, Seq[ScType])] = {
    case types => (types.last, types.dropRight(1))
  }
}

object FunctionType extends FunctionTypeBase {
  override val TypeName = "scala.Function"

  def isFunctionType(`type`: ScType): Boolean = unapply(`type`).isDefined
}

object ContextFunctionType extends FunctionTypeBase {
  override val TypeName: String = "scala.ContextFunction"

  def isContextFunctionType(tpe: ScType): Boolean = unapply(tpe).isDefined
}

object PartialFunctionType extends FunctionTypeFactory[ScTrait, (ScType, ScType)] {

  override val TypeName = "scala.PartialFunction"

  override def apply(pair: (ScType, ScType))
                    (implicit scope: ElementScope): ValueType = {
    val (returnType, parameter) = pair
    apply(Seq(parameter, returnType), "")
  }

  override protected def unapplyCollector: PartialFunction[Seq[ScType], (ScType, ScType)] = {
    case Seq(returnType, parameter) => (parameter, returnType)
  }
}

object TupleType {
  private val Scala2TupleTypeName = Scala2TupleType.TypeName
  private val Scala3EmptyTupleTypeName = "scala.EmptyTuple"
  private val Scala3TupleAppendTypeName = "scala.*:"

  def apply(types: Seq[ScType], context: PsiElement): ScType =
    apply(types, scala3 = context.isInScala3File)(context.elementScope)

  def apply(types: Seq[ScType], scala3: Boolean)(implicit scope: ElementScope): ScType = {
    if (scala3 && types.sizeIs > 22) Scala3TupleType(types)
    else Scala2TupleType(types)
  }

  def unapply(`type`: ScType): Option[Seq[ScType]] = unapply(`type`, onlyScala3 = false)

  private[api] def unapply(`type`: ScType, onlyScala3: Boolean): Option[Seq[ScType]] = {
    extractParameterizedType(`type`) match {
      case Some(pTy) =>
        extractMember(pTy.designator).flatMap { tupleClass =>
          tupleClass.qualifiedNameOpt.flatMap {
            case name if !onlyScala3 && name.startsWith(Scala2TupleTypeName) => Some(pTy.typeArguments)
            case name if name.startsWith(Scala3TupleAppendTypeName) =>
              val result = Seq.newBuilder[ScType]
              @tailrec
              def addToResult(argsOfTuple: Seq[ScType]): Boolean = argsOfTuple match {
                case Seq(comp, next) =>
                  result += comp
                  extractParameterizedType(next) match {
                    case Some(pTy) if extractMember(pTy.designator).contains(tupleClass) =>
                      addToResult(pTy.typeArguments)
                    case _ =>
                      isScala3EmptyTupleType(next)
                  }
                case _ =>
                  false
              }

              if (addToResult(pTy.typeArguments)) Some(result.result())
              else None

            case _ => None
          }
        }
      case None  =>
        if (isScala3EmptyTupleType(`type`)) Some(Seq.empty)
        else None
    }
  }

  def isTupleType(`type`: ScType): Boolean = unapply(`type`).isDefined

  def isScala3EmptyTupleType(`type`: ScType): Boolean =
    `type`.extractDesignated(expandAliases = true).exists {
      case obj: ScObject => obj.qualifiedNameOpt.contains(Scala3EmptyTupleTypeName)
      case _ => false
    }
}

object Scala2TupleType extends FunctionTypeFactory[ScClass, Seq[ScType]] {

  override val TypeName = "scala.Tuple"

  override def apply(types: Seq[ScType])
                    (implicit scope: ElementScope): ValueType =
    apply(types, types.length.toString)

  def isTupleType(`type`: ScType): Boolean = unapply(`type`).isDefined

  override protected def unapplyCollector: PartialFunction[Seq[ScType], Seq[ScType]] = {
    case types => types
  }
}

object Scala3TupleType {
  val EmptyTupleTypeName = "scala.EmptyTuple"
  val TupleConsTypeName = "scala.*:"

  def apply(types: Seq[ScType])(implicit scope: ElementScope): ScType = {
    (
      scope.getCachedObject(EmptyTupleTypeName),
      scope.getCachedClass(TupleConsTypeName)
    ) match {
      case (Some(emptyTupleObj), Some(tupleAppendClass: ScClass)) =>
        val emptyTupleType = ScalaType.designator(emptyTupleObj)
        val tupleAppendType = ScalaType.designator(tupleAppendClass)
        types.foldRight(emptyTupleType) {
          (comp, tup) => ScParameterizedType(tupleAppendType, Seq(comp, tup))
        }
      case _ =>
        api.Nothing
    }
  }

  def unapply(`type`: ScType): Option[Seq[ScType]] = TupleType.unapply(`type`, onlyScala3 = true)

  def isEmptyTupleType(`type`: ScType): Boolean =
    `type`.extractDesignated(expandAliases = true).exists {
      case obj: ScObject => obj.qualifiedNameOpt.contains(EmptyTupleTypeName)
      case _ => false
    }
}

object NamedTupleType extends FunctionTypeFactory[ScClass, Seq[(ScType, ScType)]] {
  override val TypeName = "scala.NamedTuple.NamedTuple"

//  try not to use this because it doesn't preserve the psiElement in ScLiteralType
//
//  def create(elements: Seq[(String, ScType)])(implicit scope: ElementScope): ValueType = {
//    apply(elements.map {
//      case (name, ty) =>
//        val nameType = ScLiteralType(ScStringLiteralImpl.Value(name))(ty.projectContext)
//        (nameType, ty)
//    })
//  }

  override def apply(elements: Seq[(ScType, ScType)])
                    (implicit scope: ElementScope): ValueType = {
    val (names, types) = elements.unzip
    scope.scalaNamedTupleType match {
      case Some(x) =>
        ScParameterizedType(
          ScalaType.designator(x),
          Seq(
            TupleType(names, scala3 = true),
            TupleType(types, scala3 = true)
          )
        ).asInstanceOf[ValueType]
      case None =>
        api.Nothing
    }

  }

  override protected def unapplyCollector: PartialFunction[Seq[ScType], Seq[(ScType, ScType)]] = {
    case Seq(TupleType(names), TupleType(types)) =>
      names.zip(types)
  }

  def isUnaliasedNamedTupleType(typ: ScType): Boolean =
    typ.aliasType.exists(_.ta.qualifiedNameOpt.contains(TypeName))

  def makeComponentMap(components: Seq[(ScType, ScType)]): Map[String, ScType] =
    components.iterator
      .collect { case (NameType(name), ty) => name -> ty }
      .toMap

  object NameType {
    def apply(name: String, psiElement: PsiElement)(implicit context: ProjectContext): ScType =
      ScLiteralType(ScStringLiteralImpl.Value(name), psiElement = psiElement)(context.project)

    def unapply(scType: ScType): Option[String] = from(scType)

    def from(ty: ScType): Option[String] =
      getLiteralType(ty).flatMap(_.value match {
        case ScStringLiteralImpl.Value(string) => Some(string)
        case _ => None
      })

    object WithLiteral {
      def unapply(ty: ScType): Option[(String, ScLiteralType)] =
        getLiteralType(ty).flatMap(lit => lit.value match {
          case ScStringLiteralImpl.Value(string) => Some(string -> lit)
          case _ => None
        })
    }

    @tailrec
    private def getLiteralType(ty: ScType): Option[ScLiteralType] = ty match {
      case lit: ScLiteralType => Some(lit)
      case _ if ty.isAliasType => getLiteralType(ty.removeAliasDefinitions())
      case _ => None
    }
  }
}