package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, ScTypeExt, ScalaType}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

import scala.annotation.tailrec

/**
  * @author adkozlov
  */
sealed trait FunctionTypeFactory {
  protected val typeName: String

  protected def isValid(definition: ScTypeDefinition): Boolean = definition.isInstanceOf[ScTrait]

  protected def innerApply(fullyQualifiedName: String, parameters: Seq[ScType])
                          (implicit elementScope: ElementScope): ValueType = {
    ScalaPsiManager.instance(elementScope.project)
      .getCachedClass(elementScope.scope, fullyQualifiedName)
      .collect {
        case definition: ScTypeDefinition => definition
      }.filter(isValid)
      .map(ScalaType.designator)
      .map(ScParameterizedType(_, parameters))
      .getOrElse(Nothing)
  }

  protected def innerUnapply(`type`: ScType): Option[Seq[ScType]] =
    extractForPrefix(`type`, typeName).filter {
      _.nonEmpty
    }

  @tailrec
  private def extractForPrefix(`type`: ScType, prefix: String, depth: Int = 100): Option[Seq[ScType]] =
    depth match {
      case 0 => None //hack for http://youtrack.jetbrains.com/issue/SCL-6880 to avoid infinite loop.
      case _ =>
        `type`.isAliasType match {
          case Some(AliasType(_: ScTypeAliasDefinition, Success(lower, _), _)) =>
            extractForPrefix(lower, prefix, depth - 1)
          case _ =>
            `type` match {
              case parameterizedType: ScParameterizedType => extractForPrefix(parameterizedType, prefix)
              case _ => None
            }
        }
    }

  private def extractForPrefix(parameterizedType: ScParameterizedType, prefix: String) = {
    def startsWith(definition: ScTypeDefinition) =
      Option(definition.qualifiedName).exists {
        _.startsWith(prefix)
      }

    parameterizedType.designator.extractClassType.collect {
      case (definition: ScTypeDefinition, substitutor) if startsWith(definition) =>
        (definition, substitutor.followed(parameterizedType.substitutor))
    }.flatMap {
      case (definition, followedSubstitutor) =>
        definition.getType().toOption.map {
          followedSubstitutor.subst
        }.collect {
          case ParameterizedType(_, typeArgs) => typeArgs
        }
    }
  }
}

object FunctionType extends FunctionTypeFactory {
  override protected val typeName = "scala.Function"

  def apply(returnType: ScType, parameters: Seq[ScType])
           (implicit elementScope: ElementScope): ValueType =
    innerApply(s"$typeName${parameters.length}", parameters :+ returnType)

  def unapply(`type`: ScType): Option[(ScType, Seq[ScType])] =
    innerUnapply(`type`).map { typeArguments =>
      val (parameters, Seq(resultType)) = typeArguments.splitAt(typeArguments.length - 1)
      (resultType, parameters)
    }

  def isFunctionType(`type`: ScType): Boolean = unapply(`type`).isDefined
}

object PartialFunctionType extends FunctionTypeFactory {
  override protected val typeName = "scala.PartialFunction"

  def apply(returnType: ScType, parameter: ScType)
           (implicit elementScope: ElementScope): ValueType =
    innerApply(typeName, Seq(parameter, returnType))

  def unapply(`type`: ScType): Option[(ScType, ScType)] =
    innerUnapply(`type`).filter {
      _.length == 2
    }.map { typeArguments =>
      (typeArguments(1), typeArguments.head)
    }
}

object TupleType extends FunctionTypeFactory {
  override protected val typeName = "scala.Tuple"

  override protected def isValid(definition: ScTypeDefinition): Boolean = definition.isInstanceOf[ScClass]

  def apply(components: Seq[ScType])
           (implicit elementScope: ElementScope): ValueType =
    innerApply(s"$typeName${components.length}", components)

  def unapply(`type`: ScType): Option[Seq[ScType]] = innerUnapply(`type`)
}
