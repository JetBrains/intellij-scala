package org.jetbrains.plugins.scala.lang.psi.impl.expr

import api.toplevel.templates.ScExtendsBlock
import com.intellij.util.IncorrectOperationException
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiReference, PsiClass}
import lexer.ScalaTokenTypes
import com.intellij.openapi.util.TextRange
import codehaus.groovy.antlr.parser.GroovyTokenTypes
import types.{ScDesignatorType, Nothing}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
* @author Alexander Podkhalyuzin
* Date: 14.03.2008
*/

class ScSuperReferenceImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSuperReference {
  override def toString: String = "SuperReference"

  override def getType = refClass match {
    case Some(clazz) => new ScDesignatorType(clazz)
    case _ => Nothing
  }

  def refClass = {
    val ref = getReference
    if (ref != null) ref.resolve match {
      case c : PsiClass => Some(c)
      case _ => None
    } else superClasses match {
      case None => None
      case Some(supers) => supers match {
        case Array() => None
        case some => Some(some(0))
      }
    }
  }

  override def getReference = {
    val id = findChildByType(ScalaTokenTypes.tIDENTIFIER)
    if (id == null) null else new PsiReference {
      def getElement = id
      def getRangeInElement = new TextRange(0, id.getTextLength)
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

      def resolve(): PsiElement = superClasses match {
        case None => null
        case Some(supers) => {
          val name = id.getText
          for (aSuper <- supers) {
            if (name == aSuper.getName) return aSuper
          }
          null
        }
      }

      def getVariants(): Array[Object] = superClasses match {
        case None => Array[Object]()
        case Some(supers) => supers.map{
          c => c: Object
        }.toArray
      }
    }
  }

  private def superClasses = qualifier match {
    case Some(q) => q.resolve match {
      case c : PsiClass => Some(c.getSupers)
      case _ => None
    }
    case None => {
      PsiTreeUtil.getParentOfType(this, classOf[ScExtendsBlock]) match {
        case null => None
        case eb => Some(eb.supers)
      }
    }
  }
}