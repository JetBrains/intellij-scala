package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl






import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons


import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.resolve._
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
* Date: 06.03.2008
*/

class ScReferenceExpressionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScReferenceExpression {
  override def toString: String = "ReferenceExpression"

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


  def getQualifier() = findChildByClass(classOf[ScExpression])
  
  def getCanonicalText: String = null

  def  handleElementRename(newElementName: String): PsiElement = {
    return this; //todo
  }

  def  bindToElement(element: PsiElement):PsiElement = {
    return this; //todo
  }

  def isReferenceTo(element: PsiElement): Boolean = {
    if (element.isInstanceOf[PsiNamedElement]) { 
      if (Comparing.equal(getReferencedName(), element.asInstanceOf[PsiNamedElement].getName())) return resolve() == element;
    }
    return false;
  }

  @Nullable
  def getReferencedName(): String = { 
     val nameElement: ASTNode = getNameElement();
     return if (nameElement != null) nameElement.getText() else null;
   }

  def getVariants(): Array[Object] = {
    return null
  }
  def isSoft(): Boolean = { 
    return false;
  }


  def resolve(): PsiElement = {
    getQualifier match {
      case null => {
        val processor = new ResolveProcessor(null,node.getText)
        def treeWalkUp (place : PsiElement, lastParent : PsiElement): PsiElement = {
          place match {
            case null => null
            case p => {
              if (!p.processDeclarations(processor,
                ResolveState.initial(),
                lastParent, ScReferenceExpressionImpl.this)) return null

              treeWalkUp(place.getParent, place)

              val candidates = processor.getCandidates
              if (candidates.size == 1) (candidates.toArray(new Array[ScalaResolveResult](0)))(0).getElement else null
            }
          }
        }
        return treeWalkUp(this, null)
      }
      case e => null
    }

  }

  //override def getReference = this
}