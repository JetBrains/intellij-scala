package org.jetbrains.plugins.scala.editor.documentationProvider

import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.{PsiManager, PsiElement}
import lang.psi.api.statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import lang.psi.api.toplevel.ScNamedElement
import lang.psi.api.toplevel.templates.ScTemplateBody
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScMember}
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

  def generateValueInfo(value: ScNamedElement): String = {
    val member = ScalaPsiUtil.nameContext(value) match {
      case x: ScMember => x
      case _ => return null
    }
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(member))
    buffer.toString
  }

  def generateTypeAliasInfo(alias: ScTypeAlias): String = {
    val buffer = new StringBuilder
    buffer.append(getMemberHeader(alias))
    buffer.toString
  }
}