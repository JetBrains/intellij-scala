package org.jetbrains.plugins.scala.lang.psi.types.api

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStringLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionTypeFactory.{extractMember, extractParameterizedType}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, ScLiteralType, ScParameterizedType, ScType, ScalaType, api}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec
import scala.reflect.ClassTag
import scala.util.matching.Regex

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

//noinspection ScalaWeakerAccess
//noinspection ScalaUnusedSymbol
object TupleType {
  def apply(types: Seq[ScType], context: PsiElement): ScType =
    apply(types, scala3 = context.isInScala3File)(context.elementScope)

  def apply(types: Seq[ScType], scala3: Boolean)(implicit scope: ElementScope): ScType = {
    if (scala3 && types.sizeIs > TupleN.maxTupleN) TupleHList(types)
    else TupleN(types)
  }

  def unapply(`type`: ScType): Option[Seq[ScType]] = {
    extractTupleTypes(`type`, scopeIfTailIsExpected = None).collect {
      case (types, None) => types
    }
  }

  /**
   * Returns all initial types of a tuple and a tail if `type` is a Scala 3 tuple
   *
   * @param `type` the tuple type
   * @param scopeIfTailIsExpected An optimization. If no tail is expected, we do not check whether the remaining types are <: Tuple
   */
  private[api] def extractTupleTypes(`type`: ScType, scopeIfTailIsExpected: Option[ElementScope]): Option[(Seq[ScType], Option[ScType])] = {
    extractParameterizedType(`type`) match {
      case Some(pTy) =>
        extractMember(pTy.designator).flatMap { tupleClass =>
          tupleClass.qualifiedNameOpt.flatMap {
            case fqn if TupleN.isTupleNFqn(fqn) => Some(pTy.typeArguments -> None)
            case fqn if fqn == TupleHList.ConsClassFqn =>
              val result = Seq.newBuilder[ScType]
              @tailrec
              def addToResult(tail: ParameterizedType): Option[ScType] = tail.typeArguments match {
                case Seq(comp, next) =>
                  result += comp
                  extractParameterizedType(next) match {
                    case Some(pTy) =>
                      pTy.extractClass match {
                        case Some(`tupleClass`) => addToResult(pTy)
                        case Some(clazz) if TupleN.isTupleNFqn(clazz.qualifiedName) =>
                          result ++= pTy.typeArguments
                          None
                        case _ =>
                          Some(next)
                      }
                    case _ =>
                      if (TupleHList.isEmptyTupleHList(next)) None
                      else Some(next)
                  }
                case _ =>
                  Some(tail)
              }

              val tail = addToResult(pTy)
              val types = result.result()
              Some(types -> tail)

            case _ => None
          }
        }
      case None  =>
        if (TupleHList.isEmptyTupleHList(`type`)) Some(Seq.empty -> None)
        else if (scopeIfTailIsExpected.exists(TupleHList.isTupleHList(`type`)(_))) Some(Seq.empty -> Some(`type`))
        else None
    }
  }

  /**
   * Create/Unapply Tuples with potential tail
   */
  object withTail {
    @inline
    def apply(types: Seq[ScType], tail: ScType)(implicit scope: ElementScope): ScType =
      apply(types, Some(tail))

    /**
     * If tail is None, this is a normal tuple constructor
     * If tail is Some, this returns a Scala3 tuple which ends in tail (alá types(0) *: ... *: types(n-1) *: tail)
     */
    def apply(types: Seq[ScType], tail: Option[ScType])(implicit scope: ElementScope): ScType =
      tail match {
        case None => TupleType(types, scala3 = true)
        case Some(tail) => TupleHList(types, tail)
      }

    def unapply(`type`: ScType)(implicit scope: ElementScope): Option[(Seq[ScType], Option[ScType])] =
      extractTupleTypes(`type`, scopeIfTailIsExpected = Some(scope))
  }

  object TupleN extends FunctionTypeFactory[ScClass, Seq[ScType]] {
    val maxTupleN: 22 = 22
    override val TypeName = "scala.Tuple"

    private val fqnTupleNRegex: Regex = raw"$TypeName(1[0-9]|2[0-2]|[1-9])$$".r

    def isTupleNFqn(@Nullable fqn: String): Boolean = fqn != null && fqnTupleNRegex.matches(fqn)
    def tupleNArity(fqn: String): Option[Int] = fqn match {
      case fqnTupleNRegex(n) => n.toIntOption
      case _ => None
    }

    def isTupleN(`type`: ScType): Boolean =
      `type`.extractDesignated(expandAliases = true).exists {
        case obj: ScTypeDefinition => obj.qualifiedNameOpt.exists(isTupleNFqn)
        case _ => false
      }
    def tupleNArity(`type`: ScType): Option[Int] =
      `type`.extractDesignated(expandAliases = true).flatMap {
        case obj: ScTypeDefinition => obj.qualifiedNameOpt.flatMap(tupleNArity)
        case _ => None
      }

    override def apply(types: Seq[ScType])
                      (implicit scope: ElementScope): ValueType =
      apply(types, types.length.toString)

    override protected def unapplyCollector: PartialFunction[Seq[ScType], Seq[ScType]] = {
      case types => types
    }
  }

  object TupleHList {
    val TupleBaseClassFqn = "scala.Tuple"
    val EmptyTupleClassFqn = "scala.EmptyTuple"
    val NonEmptyTupleClassFqn = "scala.NonEmptyTuple"
    val ConsClassFqn = "scala.*:"

    def tupleBaseClass(implicit scope: ElementScope): Option[PsiClass] = scope.getCachedClass(TupleBaseClassFqn)
    def emptyTupleObject(implicit scope: ElementScope): Option[ScObject] = scope.getCachedObject(EmptyTupleClassFqn)
    def consClass(implicit scope: ElementScope): Option[PsiClass] = scope.getCachedClass(ConsClassFqn)

    @inline
    def apply(types: Seq[ScType])(implicit scope: ElementScope): ScType = apply(types, None)
    @inline
    def apply(types: Seq[ScType], tail: ScType)(implicit scope: ElementScope): ScType = apply(types, Some(tail))

    /**
     * Returns a Scala3 tuple which ends in tail or EmptyTuple.
     *
     * NOTE: This is normally not the correct tuple constructor. Use [[TupleType.withTail]] instead.
     *
     * aka: types(0) *: types(1) *: ... *: types(N-1) *: tail.getOrElse(EmptyTuple)
     */
    def apply(types: Seq[ScType], tail: Option[ScType])(implicit scope: ElementScope): ScType = {
      (
        tail.orElse(emptyTupleObject.map(ScalaType.designator)),
        consClass
      ) match {
        case (Some(tailType), Some(tupleAppendClass: ScClass)) =>
          val tupleAppendType = ScalaType.designator(tupleAppendClass)
          types.foldRight(tailType) {
            (comp, tup) => ScParameterizedType(tupleAppendType, Seq(comp, tup))
          }
        case _ =>
          api.Nothing
      }
    }

    def unapply(`type`: ScType)(implicit scope: ElementScope): Option[(Seq[ScType], Option[ScType])] = withTail.unapply(`type`)

    def isEmptyTupleHList(`type`: ScType): Boolean =
      `type`.extractDesignated(expandAliases = true).exists {
        case obj: ScObject => obj.qualifiedNameOpt.contains(EmptyTupleClassFqn)
        case _ => false
      }

    def isCons(`type`: ScType): Boolean =
      `type`.extractClass.exists(_.qualifiedName == ConsClassFqn)

    def isTupleHList(`type`: ScType)(implicit scope: ElementScope): Boolean =
      tupleBaseClass.exists { tupleClass =>
        `type`.conforms(ScDesignatorType(tupleClass))
      }
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