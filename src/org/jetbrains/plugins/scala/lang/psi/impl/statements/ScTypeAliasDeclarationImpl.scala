package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.ide.util.EditSourceUtil
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.lang.ASTNode
import stubs.ScTypeAliasStub
import types.result.{TypingContext, Success}

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{Nothing, Any}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.PsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:54:54
*/

class ScTypeAliasDeclarationImpl extends ScalaStubBasedElementImpl[ScTypeAlias] with ScTypeAliasDeclaration {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTypeAliasStub) = {this(); setStub(stub); setNode(null)}

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def navigate(requestFocus: Boolean) {
    val descriptor = EditSourceUtil.getDescriptor(nameId);
    if (descriptor != null) descriptor.navigate(requestFocus)
  }

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case null => ScalaPsiElementFactory.createIdentifier(getStub.asInstanceOf[ScTypeAliasStub].getName, getManager).getPsi
    case n => n
  }
  
  override def toString: String = "ScTypeAliasDeclaration"

  def lowerBound = lowerTypeElement match {
      case Some(te) => te.getType(TypingContext.empty)
      case None => Success(Nothing, Some(this))
  }

  def upperBound = upperTypeElement match {
      case Some(te) => te.getType(TypingContext.empty)
      case None => Success(Any, Some(this))
  }

  override def upperTypeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeAliasStub].getUpperBoundTypeElement.toOption
    }
    val tUpper = findLastChildByType(ScalaTokenTypes.tUPPER_BOUND)
    if (tUpper != null) {
      PsiTreeUtil.getNextSiblingOfType(tUpper, classOf[ScTypeElement]) match {
        case null => None
        case te => Some(te)
      }
    } else None
  }

  override def lowerTypeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeAliasStub].getLowerBoundTypeElement.toOption
    }
    val tLower = findLastChildByType(ScalaTokenTypes.tLOWER_BOUND)
    if (tLower != null) {
      PsiTreeUtil.getNextSiblingOfType(tLower, classOf[ScTypeElement]) match {
        case null => None
        case te => Some(te)
      }
    } else None
  }


  override def getPresentation: ItemPresentation = {
    new ItemPresentation() {
      def getPresentableText: String = name
      def getTextAttributesKey: TextAttributesKey = null
      def getLocationString: String = "(" + ScTypeAliasDeclarationImpl.this.getContainingClass.getQualifiedName + ")"
      override def getIcon(open: Boolean) = ScTypeAliasDeclarationImpl.this.getIcon(0)
    }
  }

  override def getOriginalElement: PsiElement = super[ScTypeAliasDeclaration].getOriginalElement
}