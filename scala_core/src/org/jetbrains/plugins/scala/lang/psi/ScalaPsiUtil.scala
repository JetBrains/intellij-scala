package org.jetbrains.plugins.scala.lang.psi

import _root_.org.jetbrains.plugins.scala.lang.psi.types._
import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import api.base.{ScConstructor, ScStableCodeReferenceElement, ScModifierList}
import api.expr.ScAnnotation
import api.statements._
import api.statements.params.{ScClassParameter, ScParameter}
import api.toplevel.templates.ScTemplateBody
import api.toplevel.{ScNamedElement, ScTyped}
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.{CachedValueProvider, CachedValue, PsiFormatUtil}
import com.jniwrapper.S
import impl.toplevel.typedef.TypeDefinitionMembers
import lang.psi.impl.ScalaPsiElementFactory
import lexer.ScalaTokenTypes
import structureView.ScalaElementPresentation
/**
 * User: Alexander Podkhalyuzin
 * Date: 06.10.2008
 */

object ScalaPsiUtil {
  def namedElementSig(x: PsiNamedElement): Signature = new Signature(x.getName, Seq.empty, 0, Array[PsiTypeParameter](), ScSubstitutor.empty)

  def superValsSignatures(x: PsiNamedElement): Seq[FullSignature] = {
    val empty = Seq.empty
    val typed = x match {case x: ScTyped => x case _ => return empty}
    val context: PsiElement = nameContext(typed) match {
      case value: ScValue if value.getParent.isInstanceOf[ScTemplateBody] => value
      case value: ScVariable if value.getParent.isInstanceOf[ScTemplateBody] => value
      case _ => return empty
    }
    val clazz = context.asInstanceOf[PsiMember].getContainingClass
    val s = new FullSignature(namedElementSig(x), typed.calcType,
      x.asInstanceOf[NavigatablePsiElement], clazz)
    val t = TypeDefinitionMembers.getSignatures(clazz).get(s) match {
      //partial match
      case Some(x) => x.supers.map{_.info}
    }
    return t
  }

  def nameContext(x: PsiNamedElement): PsiElement = {
    var parent = x.getParent
    def isAppropriatePsiElement(x: PsiElement): Boolean = {
      x match {
        case _: ScValue | _: ScVariable | _: ScTypeAlias | _: ScParameter | _: PsiMethod => true
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
    //todo: remove assert
    if (modifiers == null) {
      assert(true)
    }

    //todo: AccessModifiers can produce bug?
    val buffer = new StringBuilder("")
    for (modifier <- modifiers.getNode.getChildren(null) if !isLineTerminator(modifier.getPsi)) buffer.append(modifier.getText + " ")
    return buffer.toString
  }

  def isLineTerminator(element: PsiElement): Boolean = {
    element match {
      case _: PsiWhiteSpace if element.getText.indexOf('\n') != -1 => return true
      case _ =>
    }
    return element.getNode.getElementType == ScalaTokenTypes.tLINE_TERMINATOR
  }
}