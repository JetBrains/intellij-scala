package org.jetbrains.plugins.scala.lang.psi

import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import api.base.{ScStableCodeReferenceElement, ScModifierList}
import api.statements.params.ScClassParameter
import api.statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import com.intellij.psi._
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiFormatUtil
import com.jniwrapper.S
import lang.psi.impl.ScalaPsiElementFactory
import lexer.ScalaTokenTypes
import structureView.ScalaElementPresentation
import types.PhysicalSignature

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.10.2008
 */

object ScalaPsiUtil {
  def nameContext(x: PsiNamedElement): PsiElement = {
    var parent = x.getParent
    def isAppropriatePsiElement(x: PsiElement): Boolean = {
      x match {
        case _: ScValue | _: ScVariable | _: ScTypeAlias | _: ScClassParameter | _: PsiMethod => true
        case _ => false
      }
    }
    if (isAppropriatePsiElement(x)) return x
    while (parent != null && !isAppropriatePsiElement(parent)) parent = parent.getParent
    return parent
  }

  def adjustTypes(element: PsiElement): Unit = {
    for (child <- element.getChildren) {
      child match {
        case x: ScStableCodeReferenceElement => x.resolve match {
          case clazz: PsiClass =>
            x.replace(ScalaPsiElementFactory.createReferenceFromText(clazz.getName, clazz.getManager)).
                asInstanceOf[ScStableCodeReferenceElement].bindToElement(clazz)
          case _ =>
        }
        case _ => adjustTypes(child)
      }
    }
  }

  def getMethodPresentableText(method: PsiMethod): String = {
    val buffer = new StringBuffer("")
    method match {
      case method: ScFunction => {
        return ScalaElementPresentation.getMethodPresentableText(method, false)
      }
      case _ => {
        val PARAM_OPTIONS: Int = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER
        return PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY,
          PARAM_OPTIONS | PsiFormatUtil.SHOW_PARAMETERS, PARAM_OPTIONS)
      }
    }
  }

  def getModifiersPresentableText(modifiers: ScModifierList): String = {
    val buffer = new StringBuilder(" ")
    for (modifier <- modifiers.getNode.getChildren(null) if !isLineTerminator(modifier.getPsi)) buffer.append(modifier.getText + " ")
    return buffer.substring(1).toString
  }

  def isLineTerminator(element: PsiElement): Boolean = {
    element match {
      case _: PsiWhiteSpace if element.getText.indexOf('\n') != -1 => return true
      case _ =>
    }
    return element.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR
  }
}