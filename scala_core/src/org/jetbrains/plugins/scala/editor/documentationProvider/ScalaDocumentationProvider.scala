package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.{PsiManager, PsiElement}
import lang.psi.api.statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import lang.psi.api.toplevel.ScNamedElement
import lang.psi.api.toplevel.typedef.ScTypeDefinition
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
  def generateClassInfo(clazz: ScTypeDefinition): String = {
    return clazz.name
  }

  def generateFunctionInfo(function: ScFunction): String = {
    return function.name
  }

  def generateValueInfo(value: ScNamedElement): String = {
    return value.name
  }

  def generateTypeAliasInfo(alias: ScTypeAlias): String = {
    return alias.name
  }
}