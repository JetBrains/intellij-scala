package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}

abstract class ScalaPrimaryConstructorMacroBase(nameKey: String) extends ScalaMacro(nameKey) {

  override def calculateResult(expressions: Array[Expression], context: ExpressionContext): Result =
    expressions.headOption.flatMap { expression =>
      Option(expression.calculateResult(context))
    }.map(toText)
      .map(new TextResult(_))
      .orNull

  override protected final def message(nameKey: String): String = ScalaMacro.message(nameKey)

  protected def toText(result: Result): String

  protected def extractParameter(result: Result, first: Boolean): Seq[String] = result.toString match {
    case string if string.length < 2 => Seq.empty
    case string =>
      string.substring(1, string.length - 1)
        .split(",")
        .map { l =>
          (l.split(":") match {
            case Array(s, _) if first => s
            case Array(_, s) => s
            case _ => ""
          }).trim
        }
  }
}

class ScalaPrimaryConstructorParamNamesMacro extends ScalaPrimaryConstructorMacroBase("macro.primaryConstructor.param.names") {

  override protected def toText(result: Result): String =
    extractParameter(result, first = true).commaSeparated()
}

class ScalaPrimaryConstructorParamTypesMacro extends ScalaPrimaryConstructorMacroBase("macro.primaryConstructor.param.types") {

  override protected def toText(result: Result): String = {
    val list = extractParameter(result, first = false)
    list.commaSeparated().parenthesize(list.size > 1)
  }
}

class ScalaPrimaryConstructorParamsMacro extends ScalaPrimaryConstructorMacroBase("macro.primaryConstructor.param.instances") {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result =
    context.getPsiElementAtStartOffset.parentOfType(classOf[PsiClass])
      .map {
        case scalaObject: ScObject => scalaObject.fakeCompanionClassOrCompanionClass
        case other => other
      }.collect {
      case scalaClass: ScClass => scalaClass
    }.flatMap(_.constructor)
      .map(_.parameterList)
      .map(new PsiElementResult(_))
      .orNull

  override protected def toText(result: Result): String = null
}