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
import statements.{ScFunction}
import com.intellij.openapi.progress.ProgressManager
import refactoring.util.ScalaNamesUtil
import java.lang.String
import com.intellij.codeInsight.PsiEquivalenceUtil
import toplevel.ScNamedElement
import toplevel.templates.ScTemplateBody
import toplevel.packaging.ScPackaging
import toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition, ScTypeDefinition}

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
    def eqviv(elem1: PsiElement, elem2: PsiElement): Boolean = {
      if (elem1 == null || elem2 == null || elem1.getNode == null || elem2.getNode == null) return false
      if (elem1.getNode.getElementType != elem2.getNode.getElementType) return false
      elem1 match {
        case n1: PsiNamedElement => {
          import ScalaPsiUtil.nameContext
          val n2 = elem2.asInstanceOf[PsiNamedElement]
          if (n1.getName != n2.getName) return false
          n1 match {
            case n1: ScNamedElement =>
              val context1 = nameContext(n1)
              if (context1.getContext.isInstanceOf[ScalaFile] || context1.getContext.isInstanceOf[ScPackaging]) {
                if (context1.isInstanceOf[ScTypeDefinition]) {
                  return elem1.asInstanceOf[PsiClass].getQualifiedName == elem2.asInstanceOf[PsiClass].getQualifiedName
                }
              }
              if (!context1.getContext.isInstanceOf[ScTemplateBody]) {
                return elem1 == elem2
              }
              val context2 = nameContext(n2)
              if (context2.getContext.isInstanceOf[ScalaFile] || context2.getContext.isInstanceOf[ScPackaging]) {
                if (context2.isInstanceOf[ScTypeDefinition]) {
                  return elem1.asInstanceOf[PsiClass].getQualifiedName == elem2.asInstanceOf[PsiClass].getQualifiedName
                }
              }
              if (!context2.getContext.isInstanceOf[ScTemplateBody]) {
                return elem1 == elem2
              }
              val clazz1 = ScalaPsiUtil.getContextOfType(context1, true, classOf[ScTemplateDefinition]).
                asInstanceOf[ScTemplateDefinition]
              val clazz2 = ScalaPsiUtil.getContextOfType(context2, true, classOf[ScTemplateDefinition]).
                asInstanceOf[ScTemplateDefinition]
              return eqviv(clazz1, clazz2)
            case memb1: PsiMember => {
              val memb2 = elem2.asInstanceOf[PsiMember]
              val clazz1 = memb1.getContainingClass
              val clazz2 = memb2.getContainingClass
              if (clazz1 == null || clazz2 == null) return elem1 == elem2
              return eqviv(clazz1, clazz2)
            }
            case _ => return elem1 == elem2
          }
        }
        case _ => return elem1 == elem2
      }
    }
    class Eqv(elem: PsiElement) {
      def eqv(elem2: PsiElement): Boolean = {
        eqviv(elem, elem2)
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
            val methods = td.allMethods
            for (n <- methods if !break) {
              if (n.method.getName == method.getName) {
                if (method.getContainingClass eqv n.method.getContainingClass)
                  break = true
              }
            }
            if (!break && method.getText.contains("throw new Error()") && td.isInstanceOf[ScClass] &&
              td.asInstanceOf[ScClass].isCase) {
              ScalaPsiUtil.getCompanionModule(td) match {
                case Some(td) => return isReferenceTo(td)
                case _ =>
              }
            }
            if (break) return true
          }
          case obj: ScObject if obj.isSyntheticObject => {
            ScalaPsiUtil.getCompanionModule(td) match {
              case Some(td) if td eqv obj => return true
              case _ =>
            }
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