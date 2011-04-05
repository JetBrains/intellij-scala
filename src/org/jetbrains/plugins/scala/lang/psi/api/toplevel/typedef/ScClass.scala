package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import base.ScPrimaryConstructor
import impl.ScalaPsiElementFactory
import types.ScType
import statements.params.{ScTypeParam, ScParameters}
import lexer.ScalaTokenTypes
import com.intellij.psi.PsiElement
import statements.{ScFunctionDefinition, ScParameterOwner}

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
        val count = getManager.getModificationTracker.getJavaStructureModificationCount
        if (res != null && count == modCount) return res
        val texts = getSyntheticMethodsText
        val objText = "object " + getName + "{\n  " + texts._1 + "\n  " + texts._2 + "\n" + "}"
        val next = ScalaPsiUtil.getNextStubOrPsiElement(this)
        val obj = ScalaPsiElementFactory.createObjectWithContext(objText, getParent, if (next != null) next else this)
        val objOption = obj.toOption
        objOption.foreach { (obj: ScObject) =>
          obj.setSyntheticObject()
          obj.members().foreach {
            case s: ScFunctionDefinition => s.setSynthetic() // So we find the `apply` method in ScalaPsiUti.syntheticParamForParam
            case _ =>
          }
        }
        res = objOption
        modCount = count
        companionModuleRes = res
        return res
    }
  }

  protected def typeParamString : String = if (typeParameters.length > 0) typeParameters.map(param => {
    var paramText = param.getName
    param.lowerTypeElement foreach {
      case tp => paramText = paramText + " >: " + tp.getText
    }
    param.upperTypeElement foreach {
      case tp => paramText = paramText + " <: " + tp.getText
    }
    param.viewTypeElement foreach {
      case tp => paramText = paramText + " <% " + tp.getText
    }
    param.contextBoundTypeElement foreach {
      case tp => paramText = paramText + " : " + tp.getText
    }
    paramText
  }).mkString("[", ", ", "]")
  else ""

  def getSyntheticMethodsText: (String, String) = {
    val paramString = constructor match {
      case Some(x: ScPrimaryConstructor) =>
        (if (x.parameterList.clauses.length == 1 &&
            x.parameterList.clauses.apply(0).isImplicit) "()" else "") + x.parameterList.clauses.map(c =>
          c.parameters.map(p =>
            p.name + ": " +
                    p.typeElement.map(_.getText).getOrElse("Any") +
                    (if (p.isDefaultParam) " = " + p.getDefaultExpression.map(_.getText).getOrElse("{}")
                    else if (p.isRepeatedParameter) "*" else "")).
                  mkString(if (c.isImplicit) "(implicit " else "(", ", ", ")")).mkString("")
      case None => ""
    }
    val unapplyMethodNameSuffix = constructor match {
      case Some(x: ScPrimaryConstructor) =>
        (for {
          c1 <- x.parameterList.clauses.headOption
          plast <- c1.parameters.lastOption
          if plast.isRepeatedParameter
        } yield "Seq").getOrElse("")
      case None => ""
    }
    val paramStringRes = constructor match {
      case Some(x: ScPrimaryConstructor) =>
        val clauses = x.parameterList.clauses
        if (clauses.length == 0) "scala.Boolean"
        else {
          val params = clauses(0).parameters
          if (params.length == 0) "scala.Boolean"
          else {
            val strings = params.map(p =>
              (if (p.isRepeatedParameter) "scala.Seq[" else "") +
                      p.typeElement.map(_.getText).getOrElse("scala.Any") +
                      (if (p.isRepeatedParameter) "]" else ""))
            strings.mkString("scala.Option[" + (if (strings.length > 1) "(" else ""), ", ",
              (if (strings.length > 1) ")" else "") + "]")
          }
        }
      case None => "scala.Boolean"
    }
    val typeParamStringRes =
      if (typeParameters.length > 0)
        typeParameters.map(_.name).mkString("[", ", ", "]")
      else ""

    val applyText = "def apply" + typeParamString + paramString + ": " + name + typeParamStringRes +
                " = throw new Error()"
    val unapplyText = "def unapply" + unapplyMethodNameSuffix + typeParamString + "(x$0: " + name + typeParamStringRes + "): " +
                paramStringRes + " = throw new Error()"
    (applyText, unapplyText)
  }

  def getClassToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kCLASS)

  def getObjectClassOrTraitToken = getClassToken
}