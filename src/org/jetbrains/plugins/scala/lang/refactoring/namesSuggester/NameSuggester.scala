package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester

import java.util.regex.{Matcher, Pattern}

import com.intellij.openapi.project.Project
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
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaVariableValidator.empty
import org.jetbrains.plugins.scala.lang.refactoring.util.{NameValidator, ScalaVariableValidator}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil.areClassesEquivalent

import scala.collection.mutable

/**
  * @author Alexander Podkhalyuzin
  * @since 26.06.2008
  */
object NameSuggester {

  def suggestNames(expression: ScExpression)
                  (validator: ScalaVariableValidator = empty(expression.getProject)): Set[String] = {
    val names = mutable.LinkedHashSet.empty[String]
    implicit val project = validator.getProject()

    collectTypes(expression).reverse
      .foreach(names ++= namesByType(_))
    names ++= namesByExpression(expression)

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

  private[this] def collectNames(names: mutable.LinkedHashSet[String], validator: NameValidator): Set[String] = {
    val collected = names.toSeq.map {
      case "class" => "clazz"
      case name => name
    }.filter(isIdentifier(_)) match {
      case Seq() => Seq("value")
      case seq => seq.reverse
    }

    collected.toSet[String]
      .map(validator.validateName(_, increaseNumber = true))
  }

  def suggestNamesByType(`type`: ScType): Set[String] = {
    implicit val project = obtainProject
    val validator = new NameValidator {
      override def validateName(name: String, increaseNumber: Boolean): String = name

      override def getProject(): Project = project
    }

    collectNames(namesByType(`type`), validator)
  }

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


  private def namesByType(`type`: ScType, withPlurals: Boolean = true, shortVersion: Boolean = true)
                         (implicit project: Project): mutable.LinkedHashSet[String] = {
    def pluralNames(argument: ScType): mutable.LinkedHashSet[String] = {
      val newNames = namesByType(argument, withPlurals = false, shortVersion = false)
      if (withPlurals) newNames.map(plural) else newNames
    }

    def compoundNames(tp1: ScType, tp2: ScType, separator: String): mutable.LinkedHashSet[String] =
      for {
        leftName <- namesByType(tp1, shortVersion = false)
        rightName <- namesByType(tp2, shortVersion = false)
      } yield s"$leftName$separator${rightName.capitalize}"

    def functionParametersNames(returnType: ScType, parameters: Seq[ScType]): mutable.LinkedHashSet[String] =
      parameters match {
        case Seq() => namesByType(returnType, withPlurals)
        case Seq(param) => compoundNames(param, returnType, "To")
        case _ => mutable.LinkedHashSet.empty[String]
      }

    def namesByParameters(clazz: PsiClass, parameters: Seq[ScType]): mutable.LinkedHashSet[String] = {
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
        case _ => mutable.LinkedHashSet.empty[String]
      }
    }

    def toLowerCase(name: String, length: Int): String = {
      val lowerCased = name.toLowerCase
      if (shortVersion) lowerCased.substring(0, length) else lowerCased
    }

    def byName(name: String): mutable.LinkedHashSet[String] = name match {
      case "String" => mutable.LinkedHashSet(toLowerCase(name, 3))
      case _ if name != null && name.toUpperCase == name =>
        mutable.LinkedHashSet(deleteNonLetterFromString(name).toLowerCase)
      case _ => getCamelNames(name)
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
      case valType: ValType => mutable.LinkedHashSet(valTypeName(valType))
      case TupleType(_) => mutable.LinkedHashSet("tuple")
      case FunctionType(ret, params) =>
        mutable.LinkedHashSet("function") ++ functionParametersNames(ret, params)
      case ScDesignatorType(e) => byName(e.name)
      case parameterType: TypeParameterType => byName(parameterType.name)
      case ScProjectionType(_, e, _) => byName(e.name)
      case ParameterizedType(baseType, args) =>
        val byParameters = asSet(baseType.extractClass(project))
          .flatMap(namesByParameters(_, args))
        namesByType(baseType, withPlurals) ++ byParameters
      case JavaArrayType(argument) => pluralNames(argument)
      case ScCompoundType(Seq(head, _*), _, _) => namesByType(head, withPlurals)
      case _ => mutable.LinkedHashSet.empty[String]
    }
  }

  private def namesByExpression: ScExpression => mutable.LinkedHashSet[String] = {
    case _: ScThisReference => mutable.LinkedHashSet("thisInstance")
    case _: ScSuperReference => mutable.LinkedHashSet("superInstance")
    case reference: ScReferenceElement if reference.refName != null =>
      reference.refName match {
        case name if name.toUpperCase == name => mutable.LinkedHashSet(name.toLowerCase)
        case name => getCamelNames(name)
      }
    case call: ScMethodCall => namesByExpression(call.getEffectiveInvokedExpr)
    case literal: ScLiteral if literal.isString =>
      val maybeName = Option(literal.getValue).collect {
        case string: String => string
      }.map(_.toLowerCase)

      asSet(maybeName.filter(isIdentifier(_)))
    case expression =>
      val maybeName = expression.getContext match {
        case x: ScAssignStmt => x.assignName
        case x: ScArgumentExprList => x.matchedParameters.collectFirst {
          case (matchedExpression, parameter) if matchedExpression == expression => parameter
        }.map(_.name)
        case _ => None
      }
      asSet(maybeName)
  }

  private[this] def asSet[T](option: Option[T]): mutable.LinkedHashSet[T] =
    option match {
      case Some(value) => mutable.LinkedHashSet(value)
      case _ => mutable.LinkedHashSet.empty
    }

  private def getCamelNames(name: String): mutable.LinkedHashSet[String] = {
    val result = mutable.LinkedHashSet.empty[String]
    if (name == "") return result

    val s = if (Array("get", "set", "is").exists(name.startsWith))
      name.charAt(0) match {
        case 'g' | 's' => name.substring(3, name.length)
        case _ => name.substring(2, name.length)
      }
    else name
    for (i <- 0 until s.length) {
      if (i == 0) {
        val candidate = s.substring(0, 1).toLowerCase + s.substring(1)
        result += deleteNonLetterFromStringFromTheEnd(candidate)
      }
      else if (s(i) >= 'A' && s(i) <= 'Z') {
        val candidate = s.substring(i, i + 1).toLowerCase + s.substring(i + 1)
        result += deleteNonLetterFromStringFromTheEnd(candidate)
      }
    }
    result
  }

  private def deleteNonLetterFromString(s: String): String = {
    val pattern: Pattern = Pattern.compile("[^a-zA-Z]")
    val matcher: Matcher = pattern.matcher(s)
    matcher.replaceAll("")
  }

  private def deleteNonLetterFromStringFromTheEnd(s: String): String = {
    s.reverse.dropWhile(!_.isLetter).reverse
  }
}
