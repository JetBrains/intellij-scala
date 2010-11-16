package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import base.{ScPrimaryConstructor, ScModifierList}
import com.intellij.psi.PsiModifierList
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import statements.params.ScParameters
import types.result.TypingContext
import types.ScType
import impl.ScalaPsiElementFactory
import refactoring.util.ScTypeUtil

/**
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScClass extends ScTypeDefinition with ScParameterOwner {
  def constructor: Option[ScPrimaryConstructor]

  def clauses: Option[ScParameters] = constructor match {
    case Some(x: ScPrimaryConstructor) => Some(x.parameterList)
    case None => None
  }

  @volatile
  private var companionModuleRes: Option[ScObject] = null
  @volatile
  private var modCount: Long = 0L

  def fakeCompanionModule: Option[ScObject] = {
    ScalaPsiUtil.getBaseCompanionModule(this) match {
      case Some(td: ScObject) => return None
      case _ if !isCase => return None
      case _ =>
        var res = companionModuleRes
        val count = getManager.getModificationTracker.getModificationCount
        if (res != null && count == modCount) return res
        val texts = getSyntheticMethodsText
        val objText = "object " + getName + "{\n  " + texts._1 + "\n  " + texts._2 + "\n" + "}"
        val obj = ScalaPsiElementFactory.createObjectWithContext(objText, getParent, if (getNextSibling != null)
          getNextSibling else this)
        val objOption = obj.toOption
        objOption.foreach(_.setSyntheticObject)
        res = objOption
        modCount = count
        companionModuleRes = res
        return res
    }
  }

  def getSyntheticMethodsText: (String, String) = {
    val tParamString = if (typeParameters.length > 0) typeParameters.map(param => {
      var paramText = param.getName
      param.lowerBound foreach {
        case psi.types.Nothing =>
        case tp: ScType => paramText = paramText + " >: " + ScType.canonicalText(tp)
      }
      param.upperBound foreach {
        case psi.types.Any =>
        case tp: ScType => paramText = paramText + " <: " + ScType.canonicalText(tp)
      }
      param.viewBound foreach {
        (tp: ScType) => paramText = paramText + " <% " + ScType.canonicalText(tp)
      }
      param.contextBound foreach {
        (tp: ScType) => paramText = paramText + " : " + ScType.canonicalText(ScTypeUtil.stripTypeArgs(tp))
      }
      paramText
    }).mkString("[", ", ", "]")
    else ""
    val paramString = constructor match {
      case Some(x: ScPrimaryConstructor) =>
        x.parameterList.clauses.map(c =>
          c.parameters.map(p =>
            p.name + ": " +
                    ScType.canonicalText(p.getType(TypingContext.empty).getOrElse(lang.psi.types.Any)) +
                    (if (p.isDefaultParam) " = " + p.getDefaultExpression.map(_.getText).getOrElse("{}")
                    else if (p.isRepeatedParameter) "*" else "")).
                  mkString("(", ", ", ")")).mkString("")
      case None => ""
    }
    val hasSeq = constructor match {
      case Some(x: ScPrimaryConstructor) =>
        val clauses = x.parameterList.clauses
        if (clauses.length == 0) ""
        else {
          val params = clauses(0).parameters
          if (params.length == 0) ""
          else if (params.last.isRepeatedParameter) "Seq" else ""
        }
      case None => ""
    }
    val paramStringRes = constructor match {
      case Some(x: ScPrimaryConstructor) =>
        val clauses = x.parameterList.clauses
        if (clauses.length == 0) "Boolean"
        else {
          val params = clauses(0).parameters
          if (params.length == 0) "Boolean"
          else {
            val strings = params.map(p =>
              (if (p.isRepeatedParameter) "scala.Seq[" else "") +
                      ScType.canonicalText(p.getType(TypingContext.empty).getOrElse(lang.psi.types.Any)) +
                      (if (p.isRepeatedParameter) "]" else ""))
            strings.mkString("scala.Option[" + (if (strings.length > 1) "(" else ""), ", ",
              (if (strings.length > 1) ")" else "") + "]")
          }
        }
      case None => "Boolean"
    }
    val typeParamStringRes =
      if (typeParameters.length > 0)
        typeParameters.map(_.name).mkString("[", ", ", "]")
      else ""

    val applyText = "def apply" + tParamString + paramString + ": " + getQualifiedName + typeParamStringRes +
                " = throw new Error()"
    val unapplyText = "def unapply" + hasSeq + tParamString + "(x$0: " + getQualifiedName + typeParamStringRes + "): " +
                paramStringRes + " = throw new Error()"
    (applyText, unapplyText)
  }

}