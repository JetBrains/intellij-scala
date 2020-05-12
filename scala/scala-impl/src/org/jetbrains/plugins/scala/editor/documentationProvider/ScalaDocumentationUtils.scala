package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

private object ScalaDocumentationUtils {

  // TODO: review usages, maybe propper way will be to use null / None?
  val EmptyDoc = ""

  def getKeyword(element: PsiElement): String = element match {
    case _: ScClass                     => "class "
    case _: ScObject                    => "object "
    case _: ScTrait                     => "trait "
    case _: ScFunction                  => "def "
    case c: ScClassParameter if c.isVal => "val "
    case c: ScClassParameter if c.isVar => "var "
    case _: ScValue                     => "val "
    case _: ScVariable                  => "var "
    case _                              => ""
  }

  def typeAnnotationText(elem: ScTypedDefinition)
                        (implicit typeToString: ScType => String): String = {
    val typ = elem match {
      case fun: ScFunction => fun.returnType.getOrAny
      case _               => elem.`type`().getOrAny
    }
    val typeText = typeToString(typ)
    val typeTextFixed = elem match {
      case param: ScParameter => decoratedParameterType(param, typeText)
      case _                  => typeText
    }
    s": $typeTextFixed"
  }

  private def decoratedParameterType(param: ScParameter, typeText: String): String = {
    val buffer = StringBuilder.newBuilder

    if (param.isCallByNameParameter) {
      val arrow = ScalaPsiUtil.functionArrow(param.getProject)
      buffer.append(s"$arrow ")
    }

    buffer.append(typeText)

    if (param.isRepeatedParameter) buffer.append("*")

    if (param.isDefaultParam) {
      buffer.append(" = ")
      param.getDefaultExpressionInSource match {
        case Some(expr) =>
          val text: String = expr.getText.replace(" /* compiled code */ ", "")
          val cutTo = 20
          buffer.append(text.substring(0, text.length.min(cutTo)))
          if (text.length > cutTo) buffer.append("...")
        case None => buffer.append("...")
      }
    }
    buffer.toString()
  }
}
