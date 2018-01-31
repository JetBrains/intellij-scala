package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, ScalaType, api}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

import scala.annotation.tailrec
import scala.reflect.ClassTag

/**
  * @author adkozlov
  */
sealed trait FunctionTypeFactory[D <: ScTypeDefinition, T] {

  import FunctionTypeFactory._

  protected val typeName: String

  def apply(t: T)(implicit scope: ElementScope): ValueType

  def unapply(`type`: ScType): Option[T] =
    extractForPrefix(`type`, typeName) match {
      case seq if seq.nonEmpty && unapplyCollector.isDefinedAt(seq) => Some(unapplyCollector(seq))
      case _ => None
    }

  protected final def apply(parameters: Seq[ScType], suffix: String)
                           (implicit scope: ElementScope, tag: ClassTag[D]): ValueType =
    scope.getCachedClass(typeName + suffix).collect {
      case definition: D => ScParameterizedType(ScalaType.designator(definition), parameters)
    }.getOrElse(api.Nothing)

  protected def unapplyCollector: PartialFunction[Seq[ScType], T]
}

object FunctionTypeFactory {

  @tailrec
  private def extractForPrefix(`type`: ScType, prefix: String, depth: Int = 100): Seq[ScType] = `type` match {
    case _ if depth == 0 => Seq.empty //hack for http://youtrack.jetbrains.com/issue/SCL-6880 to avoid infinite loop.
    case AliasLowerBound(lower) => extractForPrefix(lower, prefix, depth - 1)
    case ScParameterizedType(designator, arguments) if extractQualifiedName(designator).exists(_.startsWith(prefix)) => arguments
    case _ => Seq.empty
  }

  private[this] def extractQualifiedName(`type`: ScType) =
    `type`.extractClass.collect {
      case definition: ScTypeDefinition => definition
    }.flatMap(definition => Option(definition.qualifiedName))

  private[this] object AliasLowerBound {

    def unapply(`type`: ScType): Option[ScType] = `type`.isAliasType.collect {
      case AliasType(_: ScTypeAliasDefinition, Right(lower), _) => lower
    }
  }

}

object FunctionType extends FunctionTypeFactory[ScTrait, (ScType, Seq[ScType])] {

  override protected val typeName = "scala.Function"

  override def apply(pair: (ScType, Seq[ScType]))
                    (implicit scope: ElementScope): ValueType = {
    val (returnType, parameters) = pair
    apply(parameters :+ returnType, parameters.length.toString)
  }

  def isFunctionType(`type`: ScType): Boolean = unapply(`type`).isDefined

  override protected def unapplyCollector: PartialFunction[Seq[ScType], (ScType, Seq[ScType])] = {
    case types => (types.last, types.dropRight(1))
  }
}

object PartialFunctionType extends FunctionTypeFactory[ScTrait, (ScType, ScType)] {

  override protected val typeName = "scala.PartialFunction"

  override def apply(pair: (ScType, ScType))
                    (implicit scope: ElementScope): ValueType = {
    val (returnType, parameter) = pair
    apply(Seq(parameter, returnType), "")
  }

  override protected def unapplyCollector: PartialFunction[Seq[ScType], (ScType, ScType)] = {
    case Seq(returnType, parameter) => (parameter, returnType)
  }
}

object TupleType extends FunctionTypeFactory[ScClass, Seq[ScType]] {

  override protected val typeName = "scala.Tuple"

  override def apply(types: Seq[ScType])
                    (implicit scope: ElementScope): ValueType =
    apply(types, types.length.toString)

  override protected def unapplyCollector: PartialFunction[Seq[ScType], Seq[ScType]] = {
    case types => types
  }
}
