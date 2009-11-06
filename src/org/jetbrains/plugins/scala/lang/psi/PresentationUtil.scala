package org.jetbrains.plugins.scala.lang
package psi

import api.statements.params._
import org.jetbrains.plugins.scala.editor.documentationProvider.ScalaDocumentationProvider
import types.{ScType, ScSubstitutor}
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil
import com.intellij.psi._
import org.jetbrains.plugins.scala.util.ScalaUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 12.08.2009
 */

object PresentationUtil {
  def presentationString(obj: Any, substitutor: ScSubstitutor = ScSubstitutor.empty): String = {
    val res: String = obj match {
      case clauses: ScParameters => clauses.clauses.map(presentationString(_, substitutor)).mkString("")
      case clause: ScParameterClause => {
        val buffer = new StringBuilder("")
        buffer.append("(")
        if (clause.isImplicit) buffer.append("implicit ")
        buffer.append(clause.parameters.map(presentationString(_, substitutor)).mkString(", "))
        buffer.append(")")
        buffer.toString
      }
      case param: ScParameter => ScalaDocumentationProvider.parseParameter(param, presentationString(_, substitutor))
      case tp: ScType => ScType.presentableText(substitutor.subst(tp))
      case tp: PsiType => presentationString(ScType.create(tp, DecompilerUtil.obtainProject), substitutor)
      case tp: ScTypeParamClause => {
        tp.typeParameters.map(t => presentationString(t, substitutor)).mkString("[", ", ", "]")
      }
      case param: ScTypeParam => {
        var paramText = param.getName
        if (param.isContravariant) paramText = "-" + paramText
        else if (param.isCovariant) paramText = "+" + paramText
        param.lowerBound foreach {
          case psi.types.Nothing =>
          case tp: ScType => paramText = paramText + " >: " + presentationString(tp, substitutor)
        }
        param.upperBound foreach {
          case psi.types.Any =>
          case tp: ScType => paramText = paramText + " <: " + presentationString(tp, substitutor)
        }
        param.viewBound foreach {
          (tp: ScType) => paramText = paramText + " <% " + presentationString(tp, substitutor)
        }
        paramText
      }
      case params: PsiParameterList => {
        params.getParameters.map(presentationString(_, substitutor)).mkString("(", ", ", ")")
      }
      case param: PsiParameter => {
        val buffer: StringBuilder = new StringBuilder("")
        val list = param.getModifierList
        if (list == null) return ""
        val lastSize = buffer.length
        for (a <- list.getAnnotations) {
          if (lastSize != buffer.length) buffer.append(" ")
          val element = a.getNameReferenceElement();
          if (element != null) buffer.append("@").append(element.getText)
        }
        if (lastSize != buffer.length) buffer.append(" ")
        val paramType = param.getType

        val name = param.getName
        if (name != null) {
          buffer.append(name)
        }
        buffer.append(": ")
        buffer.append(presentationString(param.getType, substitutor))
        buffer.toString
      }
      case elem: PsiElement => elem.getText
      case null => ""
      case _ => obj.toString
    }
    return res.replace(ScalaUtils.typeParameter, "T")
  }
}