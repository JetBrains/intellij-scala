package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester

import java.util.regex.{Matcher, Pattern}

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiClass, PsiNamedElement}
import org.atteo.evo.inflector.English
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.{NameValidator, ScalaNamesUtil}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 * @since 26.06.2008
 */

object NameSuggester {
  private def emptyValidator(project: Project) = new NameValidator {
    def getProject(): Project = project
    def validateName(name: String, increaseNumber: Boolean): String = name
  }
  def suggestNames(expr: ScExpression): Array[String] = suggestNames(expr, emptyValidator(expr.getProject))
  def suggestNames(expr: ScExpression, validator: NameValidator): Array[String] = {
    val names = new ArrayBuffer[String]

    val types = new ArrayBuffer[ScType]()
    val typez = expr.getType(TypingContext.empty).getOrElse(null)
    if (typez != null && typez != Unit) types += typez
    expr.getTypeWithoutImplicits().foreach(types += _)
    expr.getTypeIgnoreBaseType(TypingContext.empty).foreach(types += _)
    if (typez != null && typez == Unit) types += typez

    for (tpe <- types.reverse) {generateNamesByType(tpe)(names, validator)}
    generateNamesByExpr(expr)(names, validator)

    val result = (for (name <- names if name != "" && ScalaNamesUtil.isIdentifier(name) || name == "class") yield {
      if (name != "class") name else "clazz"
    }).toList.reverse.toArray
    if (result.size > 0) result
    else Array(validator.validateName("value", increaseNumber = true))
  }

  def suggestNamesByType(typez: ScType): Array[String] = {
    val names = new ArrayBuffer[String]
    generateNamesByType(typez)(names, emptyValidator(DecompilerUtil.obtainProject))
    val result = names.map {
      case "class" => "clazz"
      case s => s
    }.filter(name => name != "" && ScalaNamesUtil.isIdentifier(name))
    if (result.length == 0) {
      Array("value")
    } else result.reverse.toArray
  }

  private def add(s: String)(implicit validator: NameValidator, names: ArrayBuffer[String]) {
    val name = validator.validateName(s, increaseNumber = true)
    if (!names.contains(name))
      names += name
  }

  private def namesByType(tpe: ScType, withPlurals: Boolean = true, shortVersion: Boolean = true)
                         (implicit validator: NameValidator): ArrayBuffer[String] = {
    val names = ArrayBuffer[String]()
    generateNamesByType(tpe, shortVersion)(names, validator, withPlurals)
    names
  }

  private def generateNamesByType(typez: ScType, shortVersion: Boolean = true)(implicit names: ArrayBuffer[String], validator: NameValidator,
                                                 withPlurals: Boolean = true) {
    val project = validator.getProject()
    implicit val typeSystem = project.typeSystem
    def addPlurals(arg: ScType) {
      def addPlural(s: String) {
        if (!withPlurals) add(s)
        else {
          s match {
            case "x" => add("xs")
            case "index" => add("indices")
            case _ => add(English.plural(s))
          }
        }
      }
      arg match {
        case ValType(name) => addPlural(name.toLowerCase)
        case ScTupleType(_) => addPlural("tuple")
        case ScFunctionType(_, _) => addPlural("function")
        case ScDesignatorType(e) =>
          val camelNames = getCamelNames(e.name)
          camelNames.foreach(addPlural)
        case _ =>
          namesByType(arg, withPlurals = false, shortVersion = false).foreach(addPlural)
      }
    }

    def addFromTwoTypes(tp1: ScType, tp2: ScType, separator: String) {
      for {
        leftName <- namesByType(tp1, shortVersion = false)
        rightName <- namesByType(tp2, shortVersion = false)
      } {
        add(s"${leftName}$separator${rightName.capitalize}")
      }
    }

    def addForFunctionType(ret: ScType, params: Seq[ScType]) = params match {
      case Seq() =>
        add("function")
        generateNamesByType(ret)
      case Seq(param) =>
        add("function")
        addFromTwoTypes(param, ret, "To")
      case _ =>
        add("function")
    }

    def addForParameterizedType(baseType: ScType, args: Seq[ScType]) {
      baseType match {
        case ScProjectionType(p, ta: ScTypeAliasDefinition, _) =>
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
            addPlurals(args(0))
          case c if needPrefix.keySet.contains(c.qualifiedName) && args.nonEmpty =>
            for {
              s <- namesByType(args(0), shortVersion = false)
              prefix = needPrefix(c.qualifiedName)
            } {
              add(prefix + s.capitalize)
            }
          case c if c.qualifiedName == eitherClassName && args.size == 2 =>
            addFromTwoTypes(args(0), args(1), "Or")
          case c if (isInheritor(c, baseMapClassName) || isInheritor(c, baseJavaMapClassName))
            && args.size == 2 =>
            addFromTwoTypes(args(0), args(1), "To")
          case c if (isInheritor(c, baseCollectionClassName) || isInheritor(c, baseJavaCollectionClassName))
            && args.size == 1 =>
            addPlurals(args(0))
          case _ =>
        }
      }
    }

    def addForNamedElementString(name: String) = {
      if (name != null && name.toUpperCase == name) {
        add(deleteNonLetterFromString(name).toLowerCase)
      } else if (name == "String") {
        add(if (shortVersion) "s" else "string")
      } else {
        generateCamelNames(name)
      }
    }

    def addForNamedElement(named: PsiNamedElement) = {
      val name = named.name
      addForNamedElementString(name)
    }

    typez match {
      case ValType(name) =>
        name match {
          case "Int" => add(if (shortVersion) "i" else "int")
          case "Unit" => add("unit")
          case "Byte" => add("byte")
          case "Long" => add(if (shortVersion) "l" else "long")
          case "Float" => add(if (shortVersion) "fl" else "float")
          case "Double" => add(if (shortVersion) "d" else "double")
          case "Short" => add(if (shortVersion) "sh" else "short")
          case "Boolean" => add(if (shortVersion) "b" else "boolean")
          case "Char" => add(if (shortVersion) "c" else "char")
          case _ =>
        }
      case ScTupleType(comps) => add("tuple")
      case ScFunctionType(ret, params) => addForFunctionType(ret, params)
      case ScDesignatorType(e) => addForNamedElement(e)
      case ScTypeParameterType(name, typeParams, lowerType, upperType, ptp) => addForNamedElementString(name)
      case ScProjectionType(p, e, _) => addForNamedElement(e)
      case ScParameterizedType(tp, args) =>
        addForParameterizedType(tp, args)
      case JavaArrayType(arg) => addPlurals(arg)
      case ScCompoundType(comps, _, _) =>
        if (comps.size > 0) generateNamesByType(comps(0))
      case _ =>
    }
  }

  @tailrec
  private def generateNamesByExpr(expr: ScExpression)(implicit names: ArrayBuffer[String], validator: NameValidator) {
    expr match {
      case _: ScThisReference => add("thisInstance")
      case _: ScSuperReference => add("superInstance")
      case x: ScReferenceElement if x.refName != null =>
        val name = x.refName
        if (name != null && name.toUpperCase == name) {
          add(name.toLowerCase)
        } else {
          generateCamelNames(name)
        }
      case x: ScMethodCall =>
        generateNamesByExpr(x.getEffectiveInvokedExpr)
      case l: ScLiteral if l.isString =>
        l.getValue match {
          case s: String if ScalaNamesUtil.isIdentifier(s.toLowerCase) => add(s.toLowerCase)
          case _ =>
        }
      case _ => expr.getContext match {
        case x: ScAssignStmt => x.assignName.foreach(add)
        case x: ScArgumentExprList => x.matchedParameters.find(_._1 == expr) match {
          case Some((_, parameter)) => add(parameter.name)
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
    for (i <- 0 to s.length - 1) {
      if (i == 0) {
        val candidate = s.substring(0, 1).toLowerCase + s.substring(1)
        add(deleteNonLetterFromStringFromTheEnd(candidate))
      }
      else if (s(i) >= 'A' && s(i) <= 'Z') {
        val candidate = s.substring(i, i + 1).toLowerCase + s.substring(i + 1)
        add(deleteNonLetterFromStringFromTheEnd(candidate))
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
    for (i <- 0 to s.length - 1) {
      if (i == 0) {
        val candidate = s.substring(0, 1).toLowerCase + s.substring(1)
        names += deleteNonLetterFromStringFromTheEnd(candidate)
      }
      else if (s(i) >= 'A' && s(i) <= 'Z') {
        val candidate = s.substring(i, i + 1).toLowerCase + s.substring(i + 1)
        names += deleteNonLetterFromStringFromTheEnd(candidate)
      }
    }
    names.toSeq
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