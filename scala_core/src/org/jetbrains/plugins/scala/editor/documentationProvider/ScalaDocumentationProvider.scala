package org.jetbrains.plugins.scala.editor.documentationProvider

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.{PsiManager, PsiElement}
import lang.psi.api.statements._
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScMember}
import lang.psi.api.toplevel.{ScNamedElement, ScTyped}
import lang.psi.ScalaPsiUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 11.11.2008
 */

class ScalaDocumentationProvider extends DocumentationProvider {
  import ScalaDocumentationProvider._
  def getDocumentationElementForLookupItem(psiManager: PsiManager, `object` : Object, element: PsiElement): PsiElement = null

  def getUrlFor(element: PsiElement, originalElement: PsiElement): String = null

  def getQuickNavigateInfo(element: PsiElement): String = {
    element match {
      case clazz: ScTypeDefinition => generateClassInfo(clazz)
      case function: ScFunction => generateFunctionInfo(function)
      case value: ScNamedElement if ScalaPsiUtil.nameContext(value).isInstanceOf[ScValue]
              || ScalaPsiUtil.nameContext(value).isInstanceOf[ScVariable] => generateValueInfo(value)
      case alias: ScTypeAlias => generateTypeAliasInfo(alias)
      case _ => null
    }
  }

  def getDocumentationElementForLink(psiManager: PsiManager, link: String, context: PsiElement): PsiElement = null

  def generateDoc(element: PsiElement, originalElement: PsiElement): String = null
}

private object ScalaDocumentationProvider {
  private def getMemberHeader(member: ScMember): String = {
    if (!member.getParent.isInstanceOf[ScTemplateBody]) return ""
    if (!member.getParent.getParent.getParent.isInstanceOf[ScTypeDefinition]) return ""
    member.getContainingClass.getName + " " + member.getContainingClass.getPresentation.getLocationString + "\n"
  }

  private def getOneLine(s: String): String = {
    val trimed = s.trim
    val i = trimed.indexOf('\n')
    if (i == -1) trimed
    else trimed.substring(0, i) + " ..."
  }

  def generateClassInfo(clazz: ScTypeDefinition): String = {
    return clazz.name
  }

  def generateFunctionInfo(function: ScFunction): String = {
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(function))
    buffer.append(ScalaPsiUtil.getModifiersPresentableText(function.getModifierList))
    buffer.append("def ")
    buffer.append(ScalaPsiUtil.getMethodPresentableText(function))
    buffer.toString
  }

  def generateValueInfo(field: ScNamedElement): String = {
    val member = ScalaPsiUtil.nameContext(field) match {
      case x: ScMember => x
      case _ => return null
    }
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(member))
    buffer.append(ScalaPsiUtil.getModifiersPresentableText(member.getModifierList))
    member match {
      case value: ScValue => {
        buffer.append("val ")
        buffer.append(field.name)
        field match {
          case typed: ScTyped => {
            val typez = typed.calcType
            if (typez != null) buffer.append(": " + ScType.presentableText(typez))
          }
          case _ =>
        }
        value match {
          case d: ScPatternDefinition => {
            buffer.append(" = ")
            buffer.append(getOneLine(d.expr.getText))
          }
          case _ =>
        }
      }
      case variable: ScVariable => {
        buffer.append("var ")
        buffer.append(field.name)
        field match {
          case typed: ScTyped => {
            val typez = typed.calcType
            if (typez != null) buffer.append(": " + ScType.presentableText(typez))
          }
          case _ =>
        }
        variable match {
          case d: ScVariableDefinition => {
            buffer.append(" = ")
            buffer.append(getOneLine(d.expr.getText))
          }
          case _ =>
        }
      }
    }
    buffer.toString
  }

  def generateTypeAliasInfo(alias: ScTypeAlias): String = {
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(alias))
    buffer.toString
  }
}