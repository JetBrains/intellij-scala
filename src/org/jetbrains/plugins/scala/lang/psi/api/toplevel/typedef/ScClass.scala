package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import base.ScPrimaryConstructor
import impl.ScalaPsiElementFactory
import lexer.ScalaTokenTypes
import com.intellij.psi.PsiElement
import statements.params.ScParameters
import statements.{ScFunction, ScFunctionDefinition, ScParameterOwner}
import caches.CachesUtil
import com.intellij.psi.util.PsiModificationTracker

/**
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScClass extends ScTypeDefinition with ScParameterOwner {
  def constructor: Option[ScPrimaryConstructor]

  def secondaryConstructors: Seq[ScFunction] = {
    functions.filter(_.isConstructor)
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

  def fakeCompanionModule: Option[ScObject] = {
    ScalaPsiUtil.getBaseCompanionModule(this) match {
      case Some(td: ScObject) => None
      case _ if !isCase => None
      case _ =>
        CachesUtil.get(this, CachesUtil.FAKE_CLASS_COMPANION,
          new CachesUtil.MyProvider[ScClass, Option[ScObject]](this, (clazz: ScClass) => {
            val texts = clazz.getSyntheticMethodsText

            // TODO SCL-3081
            //        val extendsText = {
            //          val clause: Option[ScParameterClause] = clauses.flatMap(_.clauses.take(1).headOption)
            //          (clause, typeParameters) match {
            //            case (Some(clause), Seq()) =>
            //               " extends Function" + clause.paramTypes.length + "[" + clause.paramTypes.map(_.canonicalText).mkString(", ") + ", " + getQualifiedName + "]"
            //            case _ => ""
            //          }
            //
            //        }
            val accessModifier = clazz.getModifierList.accessModifier.map(_.modifierFormattedText + " ").getOrElse("")
            val objText = accessModifier + "object " + clazz.name + "{\n  " + texts._1 + "\n  " + texts._2 + "\n" + "}"
            val next = ScalaPsiUtil.getNextStubOrPsiElement(clazz)
            val obj: ScObject =
              ScalaPsiElementFactory.createObjectWithContext(objText, clazz.getParent, if (next != null) next else clazz)
            import extensions._
            val objOption: Option[ScObject] = obj.toOption
            objOption.foreach { (obj: ScObject) =>
              obj.setSyntheticObject()
              obj.members.foreach {
                case s: ScFunctionDefinition => s.setSynthetic(clazz) // So we find the `apply` method in ScalaPsiUti.syntheticParamForParam
                case _ =>
              }
            }
            objOption
          })
          (PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))

    }
  }

  protected def typeParamString : String = if (typeParameters.length > 0) typeParameters.map(param => {
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

  def getSyntheticMethodsText: (String, String) = {
    val paramString = constructor match {
      case Some(x: ScPrimaryConstructor) =>
        (if (x.parameterList.clauses.length == 1 &&
            x.parameterList.clauses.apply(0).isImplicit) "()" else "") + x.parameterList.clauses.map(c =>
          c.parameters.map(p =>
            p.name + " : " +
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

    val applyText = "def apply" + typeParamString + paramString + " : " + name + typeParamStringRes +
                " = throw new Error()"
    val unapplyText = "def unapply" + unapplyMethodNameSuffix + typeParamString + "(x$0: " + name + typeParamStringRes + "): " +
                paramStringRes + " = throw new Error()"
    (applyText, unapplyText)
  }

  def getSyntheticImplicitMethod: Option[ScFunction]

  def getClassToken: PsiElement = findFirstChildByType(ScalaTokenTypes.kCLASS)

  def getObjectClassOrTraitToken = getClassToken
}