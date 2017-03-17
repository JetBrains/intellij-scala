package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiClass}
import org.atteo.evo.inflector.English
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil.obtainProject
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.refactoring.util.{NameValidator, ScalaTypeValidator, ScalaVariableValidator}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.areClassesEquivalent

import scala.collection.mutable

/**
  * @author Alexander Podkhalyuzin
  * @since 26.06.2008
  */
object NameSuggester {

  def suggestNames(expression: ScExpression)
                  (implicit validator: ScalaVariableValidator = ScalaVariableValidator.empty(expression.getProject)): Seq[String] = {
    val names = collectTypes(expression).reverse
      .flatMap(namesByType(_)(validator.getProject())) ++
      namesByExpression(expression)

    collectNames(names, validator)
  }

  private[this] def collectTypes(expression: ScExpression): Seq[ScType] = {
    val types = expression.getType().toOption ++
      expression.getTypeWithoutImplicits().toOption ++
      expression.getTypeIgnoreBaseType.toOption

    types.toSeq.sortWith {
      case (_, Unit) => true
      case _ => false
    }
  }

  private[this] def collectNames(names: Seq[String], validator: NameValidator): Seq[String] = {
    val filteredNames = mutable.LinkedHashSet(names: _*).map {
      case "class" => "clazz"
      case name => name
    }.filter(isIdentifier(_))

    val collected = filteredNames.toSeq match {
      case Seq() => Seq("value")
      case seq => seq.reverse
    }

    mutable.LinkedHashSet(collected: _*)
      .map(validator.validateName(_, increaseNumber = true))
      .toSeq
  }

  def suggestNamesByType(`type`: ScType)
                        (implicit validator: ScalaTypeValidator = ScalaTypeValidator.empty(obtainProject)): Seq[String] =
    collectNames(namesByType(`type`)(validator.getProject()), validator)

  private[this] def plural: String => String = {
    case "x" => "xs"
    case "index" => "indices"
    case string => English.plural(string)
  }

  private[this] def isInheritor(clazz: PsiClass, project: Project, baseFqns: Seq[String]): Boolean = {
    val psiFacade = JavaPsiFacade.getInstance(project)
    val scope = GlobalSearchScope.allScope(project)

    baseFqns.flatMap(fqn => Option(psiFacade.findClass(fqn, scope)))
      .exists(baseClass => clazz.isInheritor(baseClass, true) || areClassesEquivalent(clazz, baseClass))
  }

  import NameSuggesterUtils.camelCaseNames

  private def namesByType(`type`: ScType, withPlurals: Boolean = true, shortVersion: Boolean = true)
                         (implicit project: Project): Seq[String] = {
    def pluralNames(argument: ScType): Seq[String] = {
      val newNames = namesByType(argument, withPlurals = false, shortVersion = false)
      if (withPlurals) newNames.map(plural) else newNames
    }

    def compoundNames(tp1: ScType, tp2: ScType, separator: String): Seq[String] =
      for {
        leftName <- namesByType(tp1, shortVersion = false)
        rightName <- namesByType(tp2, shortVersion = false)
      } yield s"$leftName$separator${rightName.capitalize}"

    def functionParametersNames(returnType: ScType, parameters: Seq[ScType]): Seq[String] =
      parameters match {
        case Seq() => namesByType(returnType, withPlurals)
        case Seq(param) => compoundNames(param, returnType, "To")
        case _ => Seq.empty
      }

    def namesByParameters(clazz: PsiClass, parameters: Seq[ScType]): Seq[String] = {
      def isInheritor(baseFqns: String*) = this.isInheritor(clazz, project, baseFqns)

      val needPrefix = Map(
        "scala.Option" -> "maybe",
        "scala.Some" -> "some",
        "scala.concurrent.Future" -> "eventual",
        "scala.concurrent.Promise" -> "promised",
        "scala.util.Try" -> "tried")

      (clazz.qualifiedName, parameters) match {
        case ("scala.Array", Seq(first)) => pluralNames(first)
        case (name, Seq(first)) if needPrefix.keySet.contains(name) =>
          val prefix = needPrefix(name)
          val namesWithPrefix = namesByType(first, shortVersion = false)
            .map(prefix + _.capitalize)

          namesWithPrefix
        case ("scala.util.Either", Seq(first, second)) => compoundNames(first, second, "Or")
        case (_, Seq(first, second)) if isInheritor("scala.collection.GenMap", "java.util.Map") => compoundNames(first, second, "To")
        case (_, Seq(first)) if isInheritor("scala.collection.GenTraversableOnce", "java.lang.Iterable") => pluralNames(first)
        case _ => Seq.empty
      }
    }

    def toLowerCase(name: String, length: Int): String = {
      val lowerCased = name.toLowerCase
      if (shortVersion) lowerCased.substring(0, length) else lowerCased
    }

    def byName(name: String): Seq[String] = name match {
      case "String" => Seq(toLowerCase(name, 3))
      case _ => camelCaseNames(name)
    }

    def valTypeName(`type`: ValType): String = {
      val typeName = `type`.name

      val length = `type` match {
        case Char | Byte | Int | Long | Double => 1
        case Short | Float => 2
        case Boolean => 4
        case Unit => typeName.length
      }

      toLowerCase(typeName, length)
    }

    implicit val typeSystem = project.typeSystem
    `type` match {
      case valType: ValType => Seq(valTypeName(valType))
      case TupleType(_) => Seq("tuple")
      case FunctionType(ret, params) =>
        Seq("function") ++ functionParametersNames(ret, params)
      case ScDesignatorType(e) => byName(e.name)
      case parameterType: TypeParameterType => byName(parameterType.name)
      case ScProjectionType(_, e, _) => byName(e.name)
      case ParameterizedType(baseType, args) =>
        val byParameters = baseType.extractClass(project) match {
          case Some(clazz) => namesByParameters(clazz, args)
          case _ => mutable.LinkedHashSet.empty
        }

        namesByType(baseType, withPlurals) ++ byParameters
      case JavaArrayType(argument) => pluralNames(argument)
      case ScCompoundType(Seq(head, _*), _, _) => namesByType(head, withPlurals)
      case _ => Seq.empty
    }
  }

  private def namesByExpression: ScExpression => Seq[String] = {
    case _: ScThisReference => Seq("thisInstance")
    case _: ScSuperReference => Seq("superInstance")
    case reference: ScReferenceElement if reference.refName != null => camelCaseNames(reference.refName)
    case call: ScMethodCall => namesByExpression(call.getEffectiveInvokedExpr)
    case literal: ScLiteral if literal.isString =>
      val maybeName = Option(literal.getValue).collect {
        case string: String => string
      }.map(_.toLowerCase)

      maybeName.filter(isIdentifier(_)).toSeq
    case expression =>
      val maybeName = expression.getContext match {
        case x: ScAssignStmt => x.assignName
        case x: ScArgumentExprList => x.matchedParameters.collectFirst {
          case (matchedExpression, parameter) if matchedExpression == expression => parameter
        }.map(_.name)
        case _ => None
      }
      maybeName.toSeq
  }
}

object NameSuggesterUtils {

  private[namesSuggester] def camelCaseNames(name: String): Seq[String] = {
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
        Character.toLowerCase(char) + actualName.substring(index + 1)
    }

    names.map(_.replaceFirst(isNotLetter + "$", ""))
  }

  private[this] val isNotLetter = "[^\\p{IsAlphabetic}^\\p{IsDigit}]"
}
