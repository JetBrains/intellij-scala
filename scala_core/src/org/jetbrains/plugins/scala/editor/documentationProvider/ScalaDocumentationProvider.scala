package org.jetbrains.plugins.scala.editor.documentationProvider

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import _root_.scala.collection.immutable.HashSet
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.{PsiManager, PsiElement}
import lang.psi.api.base.ScPrimaryConstructor
import lang.psi.api.statements._
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.typedef._

import lang.psi.api.toplevel.{ScNamedElement, ScTyped}
import lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.structureView.StructureViewUtil

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
    val buffer = new StringBuilder
    val module = ModuleUtil.findModuleForPsiElement(clazz)
    if (module != null) {
      buffer.append('[').append(module.getName()).append("] ")
    }
    val locationString = clazz.getPresentation.getLocationString
    val length = locationString.length
    if (length > 1) buffer.append(locationString.substring(1, length - 1))
    if (buffer.length > 0) buffer.append("\n")
    buffer.append(ScalaPsiUtil.getModifiersPresentableText(clazz.getModifierList))
    buffer.append(clazz match {
      case _: ScObject => "object "
      case _: ScClass => "class "
      case _: ScTrait => "trait "
    })
    buffer.append(clazz.name)
    clazz match {
      case clazz: ScClass => {
        clazz.constructor match {
          case Some(x: ScPrimaryConstructor) => buffer.append(StructureViewUtil.getParametersAsString(x.parameterList, false))
          case None =>
        }
      }
      case _ =>
    }
    buffer.append(" extend")
    val types = clazz.superTypes
    if (types.length > 0) {
      for (i <- 0 to types.length - 1) {
        buffer.append(if (i == 1)  "\n  " else " ")
        if (i != 0) buffer.append("with ")
        buffer.append(ScType.presentableText(types(i)))
      }
    }
    buffer.toString
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