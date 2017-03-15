package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester

import java.util.regex.{Matcher, Pattern}

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiClass, PsiNamedElement}
import org.atteo.evo.inflector.English
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil.obtainProject
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaVariableValidator.empty
import org.jetbrains.plugins.scala.lang.refactoring.util.{NameValidator, ScalaVariableValidator}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
  * @author Alexander Podkhalyuzin
  * @since 26.06.2008
  */
object NameSuggester {

  def suggestNames(expression: ScExpression)
                  (implicit validator: ScalaVariableValidator = empty(expression.getProject)): Set[String] = {
    implicit val names = new ArrayBuffer[String]

    collectTypes(expression).reverse
      .foreach(generateNamesByType(_))
    generateNamesByExpr(expression)

    collectNames(names)
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

  private[this] def collectNames(names: Seq[String])
                                (implicit validator: NameValidator): Set[String] = {
    val collected = names.map {
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
    implicit val validator = new NameValidator {
      override def validateName(name: String, increaseNumber: Boolean): String = name

      override def getProject(): Project = obtainProject
    }

    implicit val names = new ArrayBuffer[String]
    generateNamesByType(`type`)
    collectNames(names)
  }

  private def namesByType(tpe: ScType, withPlurals: Boolean = true, shortVersion: Boolean = true)
                         (implicit validator: NameValidator): ArrayBuffer[String] = {
    val names = ArrayBuffer[String]()
    generateNamesByType(tpe, shortVersion)(names, validator, withPlurals)
    names
  }

  private[this] def plural: String => String = {
    case "x" => "xs"
    case "index" => "indices"
    case string => English.plural(string)
  }

  private def generateNamesByType(typez: ScType, shortVersion: Boolean = true)
                                 (implicit names: ArrayBuffer[String],
                                  validator: NameValidator,
                                  withPlurals: Boolean = true) {
    val project = validator.getProject()
    implicit val typeSystem = project.typeSystem

    def pluralNames(arg: ScType): Seq[String] = {
      val pluralNames = arg match {
        case valType: ValType => Seq(valType.name.toLowerCase)
        case TupleType(_) => Seq("tuple")
        case FunctionType(_, _) => Seq("function")
        case ScDesignatorType(e) => getCamelNames(e.name)
        case _ => namesByType(arg, withPlurals = false, shortVersion = false)
      }

      if (withPlurals) pluralNames.map(plural) else pluralNames
    }

    def addFromTwoTypes(tp1: ScType, tp2: ScType, separator: String) {
      for {
        leftName <- namesByType(tp1, shortVersion = false)
        rightName <- namesByType(tp2, shortVersion = false)
      } {
        names += s"$leftName$separator${rightName.capitalize}"
      }
    }

    def addForFunctionType(ret: ScType, params: Seq[ScType]) = {
      names += "function"
      params match {
        case Seq() =>
          generateNamesByType(ret)
        case Seq(param) =>
          addFromTwoTypes(param, ret, "To")
        case _ =>
      }
    }

    def addForParameterizedType(baseType: ScType, args: Seq[ScType]) {
      baseType match {
        case ScProjectionType(_, ta: ScTypeAliasDefinition, _) =>
          ta.aliasedType match {
            case Success(ExtractClass(c), _) =>
              generateNamesByType(baseType)
              inner(c)
            case _ => generateNamesByType(baseType)
          }
        case ScDesignatorType(c: PsiClass) =>
          generateNamesByType(baseType)
          inner(c)
        case _ => generateNamesByType(baseType)
      }

      def inner(classOfBaseType: PsiClass) {
        val arrayClassName = "scala.Array"
        val baseCollectionClassName = "scala.collection.GenTraversableOnce"
        val baseJavaCollectionClassName = "java.lang.Iterable"
        val baseMapClassName = "scala.collection.GenMap"
        val baseJavaMapClassName = "java.util.Map"
        val eitherClassName = "scala.util.Either"

        def isInheritor(c: PsiClass, baseFqn: String) = {
          val baseClass = JavaPsiFacade.getInstance(project).findClass(baseFqn, GlobalSearchScope.allScope(project))
          baseClass != null && (c.isInheritor(baseClass, true) || ScEquivalenceUtil.areClassesEquivalent(c, baseClass))
        }

        val needPrefix = Map(
          "scala.Option" -> "maybe",
          "scala.Some" -> "some",
          "scala.concurrent.Future" -> "eventual",
          "scala.concurrent.Promise" -> "promised",
          "scala.util.Try" -> "tried")

        classOfBaseType match {
          case c if c.qualifiedName == arrayClassName && args.nonEmpty =>
            names ++= pluralNames(args.head)
          case c if needPrefix.keySet.contains(c.qualifiedName) && args.nonEmpty =>
            for {
              s <- namesByType(args.head, shortVersion = false)
              prefix = needPrefix(c.qualifiedName)
            } {
              names += prefix + s.capitalize
            }
          case c if c.qualifiedName == eitherClassName && args.size == 2 =>
            addFromTwoTypes(args.head, args(1), "Or")
          case c if (isInheritor(c, baseMapClassName) || isInheritor(c, baseJavaMapClassName))
            && args.size == 2 =>
            addFromTwoTypes(args.head, args(1), "To")
          case c if (isInheritor(c, baseCollectionClassName) || isInheritor(c, baseJavaCollectionClassName))
            && args.size == 1 =>
            names ++= pluralNames(args.head)
          case _ =>
        }
      }
    }

    def addLowerCase(name: String, length: Int = 1) = {
      val lowerCaseName = name.toLowerCase
      names += (if (shortVersion) lowerCaseName.substring(0, length) else lowerCaseName)
    }

    def addForNamedElementString(name: String) = if (name != null && name.toUpperCase == name) {
      names += deleteNonLetterFromString(name).toLowerCase
    } else if (name == "String") {
      addLowerCase(name)
    } else {
      generateCamelNames(name)
    }

    def addForNamedElement(named: PsiNamedElement) = addForNamedElementString(named.name)

    def addValTypeName(valType: ValType, length: Int = 1) = addLowerCase(valType.name, length)

    typez match {
      case Int => addValTypeName(Int)
      case Unit => names += Unit.name
      case Byte => names += Byte.name
      case Long => addValTypeName(Long)
      case Float => addValTypeName(Float, 2)
      case Double => addValTypeName(Double)
      case Short => addValTypeName(Short, 2)
      case Boolean => addValTypeName(Boolean)
      case Char => addValTypeName(Char)
      case TupleType(_) => names += "tuple"
      case FunctionType(ret, params) => addForFunctionType(ret, params)
      case ScDesignatorType(e) => addForNamedElement(e)
      case parameterType: TypeParameterType => addForNamedElementString(parameterType.name)
      case ScProjectionType(_, e, _) => addForNamedElement(e)
      case ParameterizedType(tp, args) =>
        addForParameterizedType(tp, args)
      case JavaArrayType(argument) =>
        names ++= pluralNames(argument)
      case ScCompoundType(Seq(head, _*), _, _) => generateNamesByType(head)
      case _ =>
    }
  }

  @tailrec
  private def generateNamesByExpr(expr: ScExpression)(implicit names: ArrayBuffer[String], validator: NameValidator) {
    expr match {
      case _: ScThisReference => names += "thisInstance"
      case _: ScSuperReference => names += "superInstance"
      case x: ScReferenceElement if x.refName != null =>
        val name = x.refName
        if (name != null && name.toUpperCase == name) {
          names += name.toLowerCase
        } else {
          generateCamelNames(name)
        }
      case x: ScMethodCall =>
        generateNamesByExpr(x.getEffectiveInvokedExpr)
      case l: ScLiteral if l.isString =>
        l.getValue match {
          case s: String if isIdentifier(s.toLowerCase) => names += s.toLowerCase
          case _ =>
        }
      case _ => expr.getContext match {
        case x: ScAssignStmt => names ++= x.assignName
        case x: ScArgumentExprList => x.matchedParameters.find(_._1 == expr) match {
          case Some((_, parameter)) => names += parameter.name
          case _ =>
        }
        case _ =>
      }
    }
  }

  private def generateCamelNames(name: String)(implicit names: ArrayBuffer[String], validator: NameValidator) {
    if (name == "") return
    val s = if (Array("get", "set", "is").exists(name.startsWith))
      name.charAt(0) match {
        case 'g' | 's' => name.substring(3, name.length)
        case _ => name.substring(2, name.length)
      }
    else name
    for (i <- 0 until s.length) {
      if (i == 0) {
        val candidate = s.substring(0, 1).toLowerCase + s.substring(1)
        names += deleteNonLetterFromStringFromTheEnd(candidate)
      }
      else if (s(i) >= 'A' && s(i) <= 'Z') {
        val candidate = s.substring(i, i + 1).toLowerCase + s.substring(i + 1)
        names += deleteNonLetterFromStringFromTheEnd(candidate)
      }
    }
  }

  private def getCamelNames(name: String): Seq[String] = {
    if (name == "") return Seq.empty
    val names = new ArrayBuffer[String]
    val s = if (Array("get", "set", "is").exists(name.startsWith))
      name.charAt(0) match {
        case 'g' | 's' => name.substring(3, name.length)
        case _ => name.substring(2, name.length)
      }
    else name
    for (i <- 0 until s.length) {
      if (i == 0) {
        val candidate = s.substring(0, 1).toLowerCase + s.substring(1)
        names += deleteNonLetterFromStringFromTheEnd(candidate)
      }
      else if (s(i) >= 'A' && s(i) <= 'Z') {
        val candidate = s.substring(i, i + 1).toLowerCase + s.substring(i + 1)
        names += deleteNonLetterFromStringFromTheEnd(candidate)
      }
    }
    names
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
