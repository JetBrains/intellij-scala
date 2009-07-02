package org.jetbrains.plugins.scala.lang.refactoring.namesSuggester

import _root_.scala.collection.mutable.HashSet
import com.intellij.openapi.project.Project
import com.intellij.psi.{JavaPsiFacade, PsiClass}
import psi.types._
import psi.api.expr._
import java.util.regex.Matcher
import java.util.regex.Pattern
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import _root_.scala.collection.mutable.ArrayBuffer
import util.{NameValidator, ScalaNamesUtil}
/**
* User: Alexander.Podkhalyuz
* Date: 26.06.2008
*/

object NameSuggester {
  private def emptyValidator(project: Project) = new NameValidator {
    def getProject(): Project = project
    def validateName(name: String, increaseNumber: Boolean): String = name
  }
  def suggestNames(expr: ScExpression): Array[String] = suggestNames(expr, emptyValidator(expr.getProject))
  def suggestNames(expr: ScExpression, validator: NameValidator): Array[String] = {
    val names = new HashSet[String]
    generateNamesByType(expr.cachedType, names, validator)
    generateNamesByExpr(expr, names, validator)
    if (names.size == 0) {
      names += validator.validateName("value", true)
    }

    return (for (name <- names if name != "" && ScalaNamesUtil.isIdentifier(name) || name == "class") yield {
      if (name != "class") name else "clazz"
    }).toList.reverse.toArray
  }

  def suggestNamesByType(typez: ScType): Array[String] = {
    val names = new HashSet[String]
    generateNamesByType(typez, names, emptyValidator(null))
    if (names.size == 0) {
      names += "value"
    }
    return (for (name <- names if name != "" && ScalaNamesUtil.isIdentifier(name) || name == "class") yield {
      if (name != "class") name else "clazz"
    }).toList.reverse.toArray
  }

  private def generateNamesByType(typez: ScType, names: HashSet[String], validator: NameValidator): Unit = {
    def add(s: String) {
      names += validator.validateName(s, true)
    }
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
      case ScFunctionType(ret, params) if params.length == 0 => generateNamesByType(ret, names, validator)
      case ScFunctionType(ret, params) => add("function");
      case ScDesignatorType(e) => {
        val name = e.getName
        if (name != null && name.toUpperCase == name) {
          names += validator.validateName(deleteNonLetterFromString(name).toLowerCase, true)
        } else if (name == "String") {
          add("s")
        } else {
          generateCamelNames(names, validator, name)
        }
      }
      case ScProjectionType(p, ref) => {
        val name = ref.refName
        if (name != null && name.toUpperCase == name) {
          names += validator.validateName(deleteNonLetterFromString(name).toLowerCase, true)
        } else if (name == "String") {
          add("s")
        } else {
          generateCamelNames(names, validator, name)
        }
      }
      case ScParameterizedType(des@ScDesignatorType(c: PsiClass), Array(arg)) if c.getQualifiedName == "scala.Array" => {
        var s = ""
        arg match {
          case ValType(name) => {
            s = name + "s"
          }
          case ScTupleType(_) => s = "Tuples"
          case ScFunctionType(_,_) => s = "Functions"
          case ScDesignatorType(e) => {
            val seq: Seq[String] = getCamelNames(e.getName)
            if (seq.length > 0) {
              s = seq(seq.length - 1).substring(0,1).toUpperCase + seq(seq.length - 1).substring(1, seq(seq.length - 1).length) + "s" 
            }
          }
          case _ => 
        }
        if (s != "") add("arrayOf" + s)
        generateNamesByType(des, names, validator)
      }
      case ScParameterizedType(des, typeArgs) => {
        generateNamesByType(des, names, validator)
      }
      case ScCompoundType(comps, _, _) => {
        if (comps.size > 0) generateNamesByType(comps(0), names, validator)
      }
      case _ =>
    }
  }

  private def generateNamesByExpr(expr: ScExpression, names: HashSet[String], validator: NameValidator) {
    expr match {
      case _: ScThisReference => names += validator.validateName("thisInstance", true)
      case _: ScSuperReference => names += validator.validateName("superInstance", true)
      case x: ScReferenceElement if x.refName != null => {
        val name = x.refName
        if (name != null && name.toUpperCase == name) {
          names += validator.validateName(name.toLowerCase, true)
        } else {
          generateCamelNames(names, validator, name)
        }
      }
      case x: ScMethodCall => {
        generateNamesByExpr(x.getInvokedExpr, names, validator)
      }
      case _ =>
    }
  }

  private def generateCamelNames(names: HashSet[String], validator: NameValidator, name: String) {
    if (name == "") return
    val s1 = deleteNonLetterFromString(name)
    val s = if (Array("get", "set", "is").map(s1.startsWith(_)).contains(true))
              s1.charAt(0) match {
                case 'g' | 's' => s1.substring(3,s1.length)
                case _ => s1.substring(2,s1.length)
              }
            else s1
    for (i <- 0 to s.length - 1) {
      if (i == 0) {
        val candidate = s.substring(0, 1).toLowerCase + s.substring(1)
        names += validator.validateName(candidate, true)
      }
      else if (s(i) >= 'A' && s(i) <= 'Z') {
        val candidate = s.substring(i, i + 1).toLowerCase + s.substring(i + 1)
        names += validator.validateName(candidate, true)
      }
    }
  }

  private def getCamelNames(name: String): Seq[String] = {
    if (name == "") return Seq.empty
    val s1 = deleteNonLetterFromString(name)
    val names = new ArrayBuffer[String]
    val s = if (Array("get", "set", "is").map(s1.startsWith(_)).contains(true))
              s1.charAt(0) match {
                case 'g' | 's' => s1.substring(3,s1.length)
                case _ => s1.substring(2,s1.length)
              }
            else s1
    for (i <- 0 to s.length - 1) {
      if (i == 0) {
        val candidate = s.substring(0, 1).toLowerCase + s.substring(1)
        names += candidate
      }
      else if (s(i) >= 'A' && s(i) <= 'Z') {
        val candidate = s.substring(i, i + 1).toLowerCase + s.substring(i + 1)
        names += candidate
      }
    }
    return names.toSeq
  }

  private def deleteNonLetterFromString(s: String): String = {
    val pattern: Pattern = Pattern.compile("[^a-zA-Z]");
    val matcher: Matcher = pattern.matcher(s);
    return matcher.replaceAll("");
  }
}