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
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import com.intellij.psi.impl._
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

class ScStableCodeReferenceElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScStableCodeReferenceElement {

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

  def getVariants(): Array[Object] = _resolve(this, new CompletionProcessor(StdKinds.stableNotLastRef)).map(r => r.getElement) //todo
  def isSoft(): Boolean = false

  override def getName = getText

  override def toString: String = "CodeReferenceElement"

  object MyResolver extends ResolveCache.PolyVariantResolver[ScStableCodeReferenceElementImpl] {
    def resolve(ref: ScStableCodeReferenceElementImpl, incomplete: Boolean) = {
      _resolve(ref, new ResolveProcessor(StdKinds.stableNotLastRef /*todo*/, refName))
    }
  }

  def _resolve(ref: ScStableCodeReferenceElementImpl, processor: BaseProcessor): Array[ResolveResult] = {
    qualifier match {
      case None => {
        def treeWalkUp(place: PsiElement, lastParent: PsiElement): Unit = {
          place match {
            case null => ()
            case ie: ScImportStmt =>
              treeWalkUp(ie.getParent, ie)
            case p => {
              if (!p.processDeclarations(processor,
              ResolveState.initial(), //todo
              lastParent, ref)) return ()
              treeWalkUp(place.getParent, place)
            }
          }
        }
        treeWalkUp(ref, null)
      }
      case Some(q) => {
        q.bind match {
          case None =>
          case Some(other) => {
            other.element.processDeclarations(processor, ResolveState.initial(), //todo
            null, ScStableCodeReferenceElementImpl.this)
          }
        }
      }
    }
    processor.getCandidates.toArray
  }

  def multiResolve(incomplete: Boolean) = {
    getManager.asInstanceOf[PsiManagerEx].getResolveCache.resolveWithCaching(this, MyResolver, false, incomplete)
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
}