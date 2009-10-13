package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{ScDesignatorType, ScType, Nothing}
import _root_.org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import _root_.scala.collection.mutable.ArrayBuffer
import api.expr._
import api.toplevel.templates.ScExtendsBlock
import api.toplevel.typedef.{ScTypeDefinition, ScTemplateDefinition}
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import com.intellij.util.IncorrectOperationException
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiReference, PsiClass}
import com.intellij.openapi.util.TextRange
import com.intellij.lang.ASTNode

/**
* @author Alexander Podkhalyuzin
* Date: 14.03.2008
*/

class ScSuperReferenceImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSuperReference {
  override def toString = "SuperReference"

  def drvTemplate = qualifier match {
    case Some(q) => q.bind match {
      case Some(ScalaResolveResult(td : ScTypeDefinition, _)) => Some(td)
      case _ => None
    }
    case None => {
      val template = PsiTreeUtil.getContextOfType(this, classOf[ScTemplateDefinition], true)
      template.extendsBlock.templateParents match {
        case Some(parents) if PsiTreeUtil.isAncestor(parents, this, true) => {
          val ptemplate = PsiTreeUtil.getContextOfType(template, classOf[ScTemplateDefinition], true)
          if (ptemplate == null) None else Some(ptemplate)
        }
        case _ => Some(template)
      }
    }
  }

  def staticSuper = {
    val id = findChildByType(ScalaTokenTypes.tIDENTIFIER)
    if (id == null) None else findSuper(id)
  }

  override def getReference = {
    val id = findChildByType(ScalaTokenTypes.tIDENTIFIER)
    if (id == null) null else new PsiReference {
      def getElement = ScSuperReferenceImpl.this
      def getRangeInElement = new TextRange(0, id.getTextLength).shiftRight(id.getStartOffsetInParent)
      def getCanonicalText = resolve match {
        case c : PsiClass => c.getQualifiedName
        case _ => null
      }
      def isSoft() = false

      def handleElementRename(newElementName: String) = doRename(newElementName)
      def bindToElement(e : PsiElement) = e match {
        case c : PsiClass => doRename(c.getName)
        case _ => throw new IncorrectOperationException("cannot bind to anything but class")
      }

      private def doRename(newName : String) = {
        val parent = id.getNode.getTreeParent
        parent.replaceChild(id.getNode, ScalaPsiElementFactory.createIdentifier(newName, getManager))
        ScSuperReferenceImpl.this
      }

      def isReferenceTo(element: PsiElement) = element match {
        case c : PsiClass => c.getName == id.getText && resolve == c
        case _ => false
      }

      def resolve = findSuper(id) match {
        case Some(t) => ScType.extractClassType(t) match {case Some((c, _)) => c case None => null}
        case _ => null
      }

      def getVariants(): Array[Object] = superTypes match {
        case None => Array[Object]()
        case Some(supers) => {
          val buff = new ArrayBuffer[Object]
          supers.foreach{ t => ScType.extractClassType(t) match {
            case Some((c, _)) => buff += c
            case None =>
          }}
          buff.toArray
        }
      }
    }
  }

  def findSuper(id : PsiElement) : Option[ScType] = superTypes match {
    case None => None
    case Some(types) => {
      val name = id.getText
      for (t <- types) {
        ScType.extractClassType(t) match {
          case Some((c, s)) if name == c.getName => return Some(t)
          case _ =>
        }
      }
      None
    }
  }

  private def superTypes: Option[Seq[ScType]] = qualifier match {
    case Some(q) => q.resolve match {
      case c : PsiClass => Some(c.getSuperTypes.map {t => ScType.create(t, getProject)})
      case _ => None
    }
    case None => {
      PsiTreeUtil.getContextOfType(this, classOf[ScExtendsBlock], false) match {
        case null => None
        case eb: ScExtendsBlock => Some(eb.superTypes)
      }
    }
  }

  protected override def innerType() = Nothing
}