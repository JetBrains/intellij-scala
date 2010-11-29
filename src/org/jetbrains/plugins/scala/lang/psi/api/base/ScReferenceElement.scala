package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import _root_.org.jetbrains.plugins.scala.lang.resolve._
import _root_.scala.collection.Set
import impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.openapi.util.TextRange
import toplevel.typedef.ScTypeDefinition
import statements.{ScFunction}
import com.intellij.openapi.progress.ProgressManager
import refactoring.util.ScalaNamesUtil
import java.lang.String
import com.intellij.codeInsight.PsiEquivalenceUtil

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

trait ScReferenceElement extends ScalaPsiElement with ResolvableReferenceElement {
  override def getReference = this

  def nameId: PsiElement

  def refName: String = {
    val text: String = nameId.getText
    if (text.charAt(0) == '`' && text.length > 1) text.substring(1, text.length - 1)
    else text
  }

  def getElement = this

  def getRangeInElement: TextRange =
    new TextRange(nameId.getTextRange.getStartOffset - getTextRange.getStartOffset, getTextLength)

  def getCanonicalText: String = null

  def isSoft(): Boolean = false

  def handleElementRename(newElementName: String): PsiElement = {
    if (!ScalaNamesUtil.isIdentifier(newElementName)) return this
    val isQuoted = nameId.getText.startsWith("`")
    val id = nameId.getNode
    val parent = id.getTreeParent
    parent.replaceChild(id,
      ScalaPsiElementFactory.createIdentifier(if (isQuoted) "`" + newElementName + "`" else newElementName, getManager))
    return this
  }

  def isReferenceTo(element: PsiElement): Boolean = {
    class Eqv(elem: PsiElement) {
      def eqv(elem2: PsiElement): Boolean = {
        if (elem == null || elem2 == null || elem.getNode == null || elem2.getNode == null) return false
        if (elem.getNode.getElementType != elem2.getNode.getElementType) return false
        if (!elem.isInstanceOf[PsiClass])
          PsiEquivalenceUtil.areElementsEquivalent(elem, elem2)
        else
          elem.asInstanceOf[PsiClass].getQualifiedName == elem2.asInstanceOf[PsiClass].getQualifiedName
      }
    }
    implicit def psi2eqv(psi: PsiElement) = new Eqv(psi)
    val res = resolve
    if (res == null) {
      //case for imports with reference to Class and Object
      element match {
        case td: ScTypeDefinition => {
          ScalaPsiUtil.getCompanionModule(td) match {
            case Some(comp) => {
              val res = multiResolve(false)
              if (res.length == 2) {
                return res.find(_.getElement eqv td) != None && res.find(_.getElement eqv comp) != None
              } else return false
            }
            case _ => return false
          }
        }
        case _ => return false
      }
    }
    if (res eqv element) return true
    element match {
      case td: ScTypeDefinition if td.getName == refName => {
        res match {
          case method: PsiMethod if method.isConstructor => {
            if (td eqv method.getContainingClass) return true
          }
          case method: ScFunction if method.getName == "apply" || method.getName == "unapply" ||
            method.getName == "unapplySeq" => {
            var break = false
            for (n <- td.allMethods if !break) {
              if (n.method.getName == method.getName && (method.getContainingClass eqv n.method.getContainingClass)) break = true
            }
            if (break) return true
          }
          case _ =>
        }
      }
      case _ =>
    }
    return false
  }

  def qualifier: Option[ScalaPsiElement]

  //provides the set of possible namespace alternatives based on syntactic position
  def getKinds(incomplete: Boolean): Set[ResolveTargets.Value]

  def getVariants(implicits: Boolean, filterNotNamedVariants: Boolean): Array[Object] = getVariants()

  def getSameNameVariants: Array[ResolveResult]

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitReference(this)
  }
}