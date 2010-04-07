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

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

trait ScReferenceElement extends ScalaPsiElement with ResolvableReferenceElement {
  override def getReference = this

  def nameId: PsiElement

  def refName: String = nameId.getText.replace("`", "")

  def getElement = this

  def getRangeInElement: TextRange =
    new TextRange(nameId.getTextRange.getStartOffset - getTextRange.getStartOffset, getTextLength)

  def getCanonicalText: String = null

  def isSoft(): Boolean = false

  def handleElementRename(newElementName: String): PsiElement = {
    val isQuoted = refName.startsWith("`")
    val id = nameId.getNode
    val parent = id.getTreeParent
    parent.replaceChild(id,
      ScalaPsiElementFactory.createIdentifier(if (isQuoted) "`" + newElementName + "`" else newElementName, getManager))
    return this
  }

  def isReferenceTo(element: PsiElement): Boolean = {
    val res = resolve
    if (res == null) {
      //case for imports with reference to Class and Object
      element match {
        case td: ScTypeDefinition => {
          ScalaPsiUtil.getCompanionModule(td) match {
            case Some(comp) => {
              val res = multiResolve(false)
              if (res.length == 2) {
                return res.find(_.getElement == td) != None && res.find(_.getElement == comp) != None
              } else return false
            }
            case _ => return false
          }
        }
        case _ => return false
      }
    }
    if (res == element) return true
    element match {
      case td: ScTypeDefinition if td.getName == refName => {
        res match {
          case method: ScFunction if method.getName == "apply" || method.getName == "unapply" ||
            method.getName == "unapplySeq" => {
            val clazz = method.getContainingClass
            if (clazz == td) return true
            if (td.isInheritor(clazz, true)) return true
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

  def getVariants(implicits: Boolean): Array[Object] = getVariants()

  def getSameNameVariants: Array[ResolveResult]

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitReference(this)
  }
}