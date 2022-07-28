package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.genericTypes.{GenericTypeNamesProvider, TypePluralNamesProvider}
import org.jetbrains.plugins.scala.lang.refactoring.util.{ScalaTypeValidator, ScalaValidator, ScalaVariableValidator}

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable

object NameSuggester {

  private val DefaultName = "value"

  def suggestNames(expression: ScExpression, validator: ScalaVariableValidator = ScalaVariableValidator.empty): ArraySeq[String] =
    suggestNames(expression, validator, collectTypes(expression))

  def suggestNames(expression: ScExpression, validator: ScalaVariableValidator, types: Seq[ScType]): ArraySeq[String] =
    collectNames(namesByExpression(expression, types), validator)

  private def collectTypes(expression: ScExpression): Seq[ScType] = {
    val types = expression.`type`().toOption ++
      expression.getTypeWithoutImplicits().toOption ++
      expression.getTypeIgnoreBaseType.toOption

    types.toSeq.sortWith {
      case (_, t) if t.isUnit => true
      case _ => false
    }.reverse
  }

  private def namesByTypes(types: Seq[ScType]): Seq[String] = {
    types.flatMap(namesByType(_))
  }

  private def collectNames(names: Seq[String], validator: ScalaValidator): ArraySeq[String] = {

    val filteredNames = mutable.LinkedHashSet.newBuilder.addAll(names).result().map {
      case "class" => "clazz"
      case name => name
    }.filter(isIdentifier)

    val collected = filteredNames.toSeq match {
      case Seq() => Seq(DefaultName)
      case seq => seq.reverse
    }

    mutable.LinkedHashSet(collected: _*)
      .map(validator.validateName)
      .to(ArraySeq)
  }

  def suggestNamesByType(`type`: ScType, validator: ScalaTypeValidator = ScalaTypeValidator.empty): Seq[String] =
    collectNames(namesByType(`type`), validator)

  class UniqueNameSuggester(defaultName: String = DefaultName) extends (ScType => String) {

    private val counter = mutable.Map.empty[String, Int].withDefaultValue(-1)

    override def apply(`type`: ScType): String =
      this (suggestNamesByType(`type`))

    def apply(names: Iterable[String]): String = {
      val name = names.headOption.getOrElse(defaultName)
      counter(name) += 1

      name + (counter(name) match {
        case 0 => ""
        case i => i
      })
    }
  }

  @tailrec
  private[namesSuggester] def namesByType(`type`: ScType, withPlurals: Boolean = true, shortVersion: Boolean = true): Seq[String] = {
    def toLowerCase(name: String, length: Int): String = {
      val lowerCased = name.toLowerCase
      if (shortVersion) lowerCased.substring(0, length) else lowerCased
    }

    def byName(name: String): Seq[String] = name match {
      case "String" => Seq(toLowerCase(name, 3))
      case _ => camelCaseNames(name)
    }

    val stdTypes = `type`.projectContext.stdTypes
    import stdTypes._

    def valTypeName(`type`: ValType): String = {
      val typeName = `type`.name

      val length = `type` match {
        case Char | Byte | Int | Long | Double => 1
        case Short | Float => 2
        case Boolean => 4
        case _ => typeName.length
      }

      toLowerCase(typeName, length)
    }

    `type` match {
      case valType: ValType => Seq(valTypeName(valType))
      case ScDesignatorType(e) => byName(e.name)
      case parameterType: TypeParameterType => byName(parameterType.name)
      case ScProjectionType(_, e) => byName(e.name)
      case ScCompoundType(Seq(head, _*), _, _) => namesByType(head, withPlurals)
      case JavaArrayType(argument) =>
        TypePluralNamesProvider.pluralizeNames(argument)
      case genericType: ScParameterizedType =>
        GenericTypeNamesProvider.providers
          .flatMap(_.names(genericType))
      case _ => Seq.empty
    }
  }

  private def namesByExpression(expression: ScExpression): Seq[String] =
    namesByExpression(expression, collectTypes(expression))

  private def namesByExpression(expression: ScExpression, types: Seq[ScType]): Seq[String] = expression match {
    case _: ScThisReference => Seq("thisInstance")
    case _: ScSuperReference => Seq("superInstance")
    case reference: ScReference if reference.refName != null =>
      camelCaseNames(reference.refName)
    case definition: ScNewTemplateDefinition =>
      val parameters = definition.firstConstructorInvocation.toSeq
        .flatMap(_.matchedParameters)

      enhancedNames(parameters, types)
    case invocation: MethodInvocation =>
      enhancedNames(invocation.matchedParameters, types)
    case literal: ScLiteral if literal.isString =>
      Option(literal.getValue).collect {
        case string: String if isIdentifier(string.toLowerCase) => string
      }.flatMap(string => camelCaseNames(string).headOption).toSeq
    case expression =>
      val maybeName = expression.getContext match {
        case x: ScAssignment => x.referenceName
        case x: ScArgumentExprList => x.matchedParameters.collectFirst {
          case (matchedExpression, parameter) if matchedExpression == expression => parameter
        }.map(_.name)
        case _ => None
      }
      maybeName.toSeq ++ namesByTypes(types)
  }

  private def enhancedNames(parameters: Seq[(ScExpression, Parameter)],
                            types: Seq[ScType]): Seq[String] = {
    val namesByParameters = parameters.collect {
      case (expression, parameter) if parameter.name == "name" => expression
    }.flatMap(namesByExpression)

    val names = namesByTypes(types)

    names ++ compoundNames(namesByParameters, names) ++ namesByParameters
  }

  private[namesSuggester] def compoundNames(firstNames: Seq[String],
                                            lastNames: Seq[String],
                                            separator: String = ""): Seq[String] =
    for {
      firstName <- firstNames
      lastName <- lastNames
    } yield s"$firstName$separator${lastName.capitalize}"

  private def camelCaseNames(name: String): Seq[String] = {
    val actualName = name match {
      case _ if StringUtil.isEmpty(name) =>
        return Seq.empty
      case _ if name.toUpperCase == name =>
        return Seq(name.toLowerCase)
          .map(_.replaceAll(isNotLetter, ""))
      case _ =>
        val beginIndex = name match {
          case _ if name.startsWith("get") => 3
          case _ if name.startsWith("set") => 3
          case _ if name.startsWith("is") => 2
          case _ => 0
        }
        name.substring(beginIndex)
    }

    val names = actualName.zipWithIndex.collect {
      case (char, index) if index == 0 || char.isLetter && char.isUpper =>
        Character.toLowerCase(char).toString + actualName.substring(index + 1)
    }

    names.map(_.replaceFirst(isNotLetter + "$", ""))
  }

  private val isNotLetter = "[^\\p{IsAlphabetic}]"
}
