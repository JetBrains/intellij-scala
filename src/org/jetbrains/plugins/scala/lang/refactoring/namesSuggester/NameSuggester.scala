package org.jetbrains.plugins.scala
package lang
package refactoring
package namesSuggester

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import psi.types._
import psi.api.expr._
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import _root_.scala.collection.mutable.ArrayBuffer
import result.TypingContext
import util.{NameValidator, ScalaNamesUtil}
import extensions.{toPsiNamedElementExt, toPsiClassExt}
import org.atteo.evo.inflector.English

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
    expr.getTypeWithoutImplicits(TypingContext.empty).foreach(types += _)
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
    generateNamesByType(typez)(names, emptyValidator(null))
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

  private def generateNamesByType(typez: ScType)(implicit names: ArrayBuffer[String], validator: NameValidator,
                                                 withPlurals: Boolean = true) {
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
          camelNames.foreach(addPlural(_))
        case _ =>
          val newNames = new ArrayBuffer[String]()
          generateNamesByType(arg)(newNames, emptyValidator(validator.getProject()), withPlurals = false)
          newNames.foreach(addPlural(_))
      }
    }
    val plurals = Seq("scala.Array", "scala.collection.Seq",
      "scala.collection.mutable.Seq", "scala.collection.immutable.Seq", "scala.collection.immutable.List")
    val projectionMap = Map("Seq" -> "scala", "List" -> "scala")
    typez match {
      case ValType(name) => {
        name match {
          case "Int" => add("i")
          case "Unit" => add("unit")
          case "Byte" => add("byte")
          case "Long" => add("l")
          case "Float" => add("fl")
          case "Double" => add("d")
          case "Short" => add("sh")
          case "Boolean" => add("b")
          case "Char" => add("c")
          case _ =>
        }
      }
      case ScTupleType(comps) => add("tuple")
      case ScFunctionType(ret, params) if params.length == 0 => generateNamesByType(ret)
      case ScFunctionType(ret, params) => add("function")
      case ScDesignatorType(e) => {
        val name = e.name
        if (name != null && name.toUpperCase == name) {
          add(deleteNonLetterFromString(name).toLowerCase)
        } else if (name == "String") {
          add("s")
        } else {
          generateCamelNames(name)
        }
      }
      case ScProjectionType(p, e, _) =>
        val name = e.name
        if (name != null && name.toUpperCase == name) {
          add(deleteNonLetterFromString(name).toLowerCase)
        } else if (name == "String") {
          add("s")
        } else {
          generateCamelNames(name)
        }
      case ScParameterizedType(ScProjectionType(p, e, _), Seq(arg)) if projectionMap.get(e.name) != None &&
        projectionMap.get(e.name) == ScType.extractClass(p, Some(validator.getProject())).map(_.qualifiedName) =>
        addPlurals(arg)
      case ScParameterizedType(des@ScDesignatorType(c: PsiClass), Seq(arg)) if plurals.contains(c.qualifiedName) =>
        addPlurals(arg)
      case JavaArrayType(arg) => addPlurals(arg)
      case ScParameterizedType(des, typeArgs) =>
        generateNamesByType(des)
      case ScCompoundType(comps, _, _) => {
        if (comps.size > 0) generateNamesByType(comps(0))
      }
      case _ =>
    }
  }

  private def generateNamesByExpr(expr: ScExpression)(implicit names: ArrayBuffer[String], validator: NameValidator) {
    expr match {
      case _: ScThisReference => add("thisInstance")
      case _: ScSuperReference => add("superInstance")
      case x: ScReferenceElement if x.refName != null => {
        val name = x.refName
        if (name != null && name.toUpperCase == name) {
          add(name.toLowerCase)
        } else {
          generateCamelNames(name)
        }
      }
      case x: ScMethodCall => {
        generateNamesByExpr(x.getEffectiveInvokedExpr)
      }
      case _ => expr.getContext match {
        case x: ScAssignStmt => x.assignName.foreach(add(_))
        case x: ScArgumentExprList => x.matchedParameters.getOrElse(Seq.empty).find(_._1 == expr) match {
          case Some((_, parameter)) => add(parameter.name)
          case _ =>
        }
        case _ =>
      }
    }
  }

  private def generateCamelNames(name: String)(implicit names: ArrayBuffer[String], validator: NameValidator) {
    if (name == "") return
    val s = if (Array("get", "set", "is").map(name.startsWith(_)).contains(elem = true))
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
    val s = if (Array("get", "set", "is").map(name.startsWith(_)).contains(elem = true))
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