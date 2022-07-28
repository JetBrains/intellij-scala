package org.jetbrains.plugins.scala
package lang.refactoring.ui

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.{PsiElement, PsiMethod, PsiModifier}
import com.intellij.refactoring.classMembers.MemberInfoBase
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiPresentationUtils, ScalaPsiUtil}

abstract class ScalaMemberInfoBase[Member <: PsiElement](member: Member) extends MemberInfoBase[Member](member: Member) {

  member match {
    case method: PsiMethod =>
      displayName = ScalaPsiPresentationUtils.methodPresentableText(method)
      val (superMethod, containingClass) = method match {
        case scFun: ScFunction => (scFun.superMethod, scFun.containingClass)
        case _ => (method.findSuperMethods().headOption, method.getContainingClass)
      }
      superMethod match {
        case Some(_: ScFunctionDefinition) => overrides = java.lang.Boolean.TRUE
        case Some(_: ScFunctionDeclaration) => overrides = java.lang.Boolean.FALSE
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
      displayName = ScalaMemberInfoBase.getTypeDefinitionPresentableText(clazz)
      isStatic = clazz.containingClass.isInstanceOf[ScObject]
      overrides = null
    case ta: ScTypeAlias =>
      displayName = ScalaMemberInfoBase.getTypeAliasPresentableText(ta)
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
      displayName = ScalaMemberInfoBase.getValOrVarPresentableText(elem)
      isStatic = false
      overrides = null
    case _ =>
      isStatic = false
      displayName = ""
      overrides = null
  }
}

object ScalaMemberInfoBase {

  private def getTypeDefinitionPresentableText(typeDefinition: ScTypeDefinition): String =
    if (typeDefinition.nameId != null) typeDefinition.nameId.getText else ScalaBundle.message("presentable.definition.unnamed")

  private def getTypeAliasPresentableText(typeAlias: ScTypeAlias): String =
    if (typeAlias.nameId != null) typeAlias.nameId.getText else ScalaBundle.message("presentable.type.unnamed")

  private def getValOrVarPresentableText(elem: ScNamedElement): String = {
    val typeText = elem match {
      case typed: Typeable => ": " + typed.`type`().getOrAny.presentableText(typed)
      case _ => ""
    }
    val keyword = ScalaPsiUtil.nameContext(elem) match {
      case _: ScVariable                          => ScalaKeyword.VAR
      case _: ScValue                             => ScalaKeyword.VAL
      case param: ScClassParameter if param.isVar => ScalaKeyword.VAR
      case param: ScClassParameter if param.isVal => ScalaKeyword.VAL
      case _                                      => ""
    }
    s"$keyword ${elem.name}$typeText"
  }
}
