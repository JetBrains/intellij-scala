package org.jetbrains.plugins.scala
package lang.refactoring.ui

import com.intellij.refactoring.classMembers.MemberInfoBase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import com.intellij.psi.{PsiElement, PsiModifier, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.lang.java.JavaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTemplateDefinition, ScObject}
import org.jetbrains.plugins.scala.lang.structureView.ScalaElementPresentation

/**
 * Nikolay.Tropin
 * 2014-05-23
 */
abstract class ScalaMemberInfoBase[Member <: PsiElement](member: Member) extends MemberInfoBase[Member](member: Member) {

  member match {
    case method: PsiMethod =>
      displayName = ScalaPsiUtil.getMethodPresentableText(method)
      val (superMethod, containingClass) = method match {
        case scFun: ScFunction => (scFun.superMethod, scFun.containingClass)
        case _ => (method.findSuperMethods().headOption, method.getContainingClass)
      }
      superMethod match {
        case Some(m: ScFunctionDefinition) => overrides = java.lang.Boolean.TRUE
        case Some(m: ScFunctionDeclaration) => overrides = java.lang.Boolean.FALSE
        case Some(m) if m.getLanguage.isInstanceOf[JavaLanguage] =>
          //for java only
          overrides = if (!m.hasModifierProperty(PsiModifier.ABSTRACT)) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE
        case _ => overrides = null
      }
      isStatic = containingClass match {
        case _: ScObject => true
        case _: ScTemplateDefinition => false
        case _ => method.hasModifierProperty(PsiModifier.STATIC)
      }
    case clazz: ScTypeDefinition =>
      displayName = ScalaElementPresentation.getTypeDefinitionPresentableText(clazz)
      isStatic = clazz.containingClass.isInstanceOf[ScObject]
      overrides = null
    case ta: ScTypeAlias =>
      displayName = ScalaElementPresentation.getTypeAliasPresentableText(ta)
      isStatic = ta.containingClass.isInstanceOf[ScObject]
      overrides = null
    case pDef: ScPatternDefinition =>
      displayName = pDef.pList.getText
      isStatic = pDef.containingClass.isInstanceOf[ScObject]
      overrides = null
    case varDef: ScVariableDefinition =>
      displayName = varDef.pList.getText
      isStatic = varDef.containingClass.isInstanceOf[ScObject]
      overrides = null
    case elem: ScNamedElement =>
      displayName = ScalaElementPresentation.getValOrVarPresentableText(elem)
      isStatic = false
      overrides = null
    case _ =>
      isStatic = false
      displayName = ""
      overrides = null
  }
}