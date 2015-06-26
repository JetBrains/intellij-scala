package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScClass extends ScTypeDefinition with ScParameterOwner {
  def constructor: Option[ScPrimaryConstructor]

  def secondaryConstructors: Seq[ScFunction] = {
    functions.filter(_.isConstructor)
  }

  def constructors: Array[PsiMethod] = {
    (secondaryConstructors ++ constructor.toSeq).toArray
  }

  def clauses: Option[ScParameters] = constructor match {
    case Some(x: ScPrimaryConstructor) => Some(x.parameterList)
    case None => None
  }

  def addEmptyParens() {
    clauses match {
      case Some(c) =>
        val clause = ScalaPsiElementFactory.createClauseFromText("()", getManager)
        c.addClause(clause)
      case _ =>
    }
  }

  protected def typeParamString : String = if (typeParameters.nonEmpty) typeParameters.map(param => {
    var paramText = param.name
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

  def getSyntheticMethodsText: List[String] = {
    val paramString = constructor match {
      case Some(x: ScPrimaryConstructor) =>
        (if (x.parameterList.clauses.length == 1 &&
            x.parameterList.clauses.head.isImplicit) "()" else "") + x.parameterList.clauses.map(c =>
          c.parameters.map(p =>
            p.name + " : " +
                    p.typeElement.fold("Any")(_.getText) +
                    (if (p.isDefaultParam) " = " + p.getDefaultExpression.fold("{}")(_.getText)
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
        if (clauses.isEmpty) "scala.Boolean"
        else {
          val params = clauses.head.parameters
          if (params.isEmpty) "scala.Boolean"
          else {
            val strings = params.map(p =>
              (if (p.isRepeatedParameter) "scala.Seq[" else "") +
                       p.typeElement.fold("scala.Any")(_.getText) +
                      (if (p.isRepeatedParameter) "]" else ""))
            strings.mkString("scala.Option[" + (if (strings.length > 1) "(" else ""), ", ",
              (if (strings.length > 1) ")" else "") + "]")
          }
        }
      case None => "scala.Boolean"
    }
    val typeParamStringRes =
      if (typeParameters.nonEmpty)
        typeParameters.map(_.name).mkString("[", ", ", "]")
      else ""

    val applyText = "def apply" + typeParamString + paramString + " : " + name + typeParamStringRes +
                " = throw new Error()"
    val unapplyText = "def unapply" + unapplyMethodNameSuffix + typeParamString + "(x$0: " + name + typeParamStringRes + "): " +
                paramStringRes + " = throw new Error()"
    if (hasModifierProperty("abstract")) List(unapplyText)
    else List(applyText, unapplyText)
  }

  def getSyntheticImplicitMethod: Option[ScFunction]

  def getClassToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kCLASS)

  def getObjectClassOrTraitToken = getClassToken
}