package org.jetbrains.plugins.scala.lang.psi.impl.base

import org.jetbrains.plugins.scala.lang._
import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import psi.ScalaPsiElementImpl
import psi.api.base._
import psi.types._
import psi.api.toplevel.typedef.ScTypeDefinition
import psi.impl.ScalaPsiElementFactory
import resolve._

import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.icons.Icons

import com.intellij.psi.PsiElement
import com.intellij.openapi.util._
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScReferenceElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReferenceElement {

  def getElement = this

  def getRangeInElement: TextRange = {
    val nameElement: ASTNode = getNameElement()
    val startOffset: Int = if (nameElement != null) nameElement.getStartOffset()
    else getNode().getTextRange().getEndOffset();
    return new TextRange(startOffset - getNode().getStartOffset(), getTextLength());
  }

  def getNameElement(): ASTNode = {
    return getNode().findChildByType(ScalaTokenTypes.tIDENTIFIER);
  }


  def getQualifier: PsiElement = {
    if (node.getFirstChildNode.getElementType != ScalaTokenTypes.tIDENTIFIER) {
      node.getFirstChildNode.getPsi
    }
    else null
  }

  def getCanonicalText: String = null

  def handleElementRename(newElementName: String): PsiElement = {
    return this;
    //todo
  }

  def bindToElement(element: PsiElement): PsiElement = {
    return this;
    //todo
  }

  def isReferenceTo(element: PsiElement): Boolean = {
    if (element.isInstanceOf[PsiNamedElement]) {
      if (Comparing.equal(getReferencedName(), element.asInstanceOf[PsiNamedElement].getName())) return resolve() == element;
    }
    return false;
  }

  @Nullable
  def getReferencedName(): String = {
    val nameElement: ASTNode = getNameElement()
    return if (nameElement != null) nameElement.getText() else null
  }

  def getVariants(): Array[Object] = {
    return null
  }
  def isSoft(): Boolean = false

  override def getName = getText

  override def toString: String = "CodeReferenceElement"

  def multiResolve(incomplete: Boolean) = {
    val processor = new StableMemberProcessor(refName)
    qualifierRef match {
      case None => {
        def treeWalkUp(place: PsiElement, lastParent: PsiElement): Unit = {
          place match {
            case null => ()
            case p => {
              if (!p.processDeclarations(processor,
              ResolveState.initial(),  //todo
              lastParent, ScReferenceElementImpl.this)) return ()
              treeWalkUp(place.getParent, place)
            }
          }
        }
        treeWalkUp(this, null)
      }
      case Some(q) => {
        q.bind match {
          case None =>
          case Some(other) => {
            other.element.processDeclarations(processor, ResolveState.initial(), //todo
            null, ScReferenceElementImpl.this)
          }
        }
      }
    }
    processor.getCandidates.toArray(new Array[ResolveResult](0))
  }

  def getType() = {
    bind match {
      case None => null
      case Some(ScalaResolveResult(td: ScTypeDefinition, s)) => new ScParameterizedType(td, s)
      case Some(ScalaResolveResult(other, _)) => new ScDesignatorType(other)
    }
  }

  def nameNode: PsiElement = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  def refName: String = nameNode.getText

  def qualifierRef = findChild(classOf[ScReferenceElement])
}