package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.ide.util.EditSourceUtil
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{Any, Nothing}

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
    val descriptor = EditSourceUtil.getDescriptor(nameId)
    if (descriptor != null) descriptor.navigate(requestFocus)
  }

  def nameId = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER) match {
    case null => ScalaPsiElementFactory.createIdentifier(getStub.asInstanceOf[ScTypeAliasStub].getName, getManager).getPsi
    case n => n
  }
  
  override def toString: String = "ScTypeAliasDeclaration: " + name

  def lowerBound = lowerTypeElement match {
      case Some(te) => te.getType(TypingContext.empty)
      case None => Success(Nothing, Some(this))
  }

  def upperBound = upperTypeElement match {
      case Some(te) => te.getType(TypingContext.empty)
      case None => Success(Any, Some(this))
  }

  override def upperTypeElement: Option[ScTypeElement] = {
    import org.jetbrains.plugins.scala.extensions._
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeAliasStub].getUpperBoundTypeElement.toOption
    }
    val tUpper = findLastChildByType[PsiElement](ScalaTokenTypes.tUPPER_BOUND)
    if (tUpper != null) {
      PsiTreeUtil.getNextSiblingOfType(tUpper, classOf[ScTypeElement]) match {
        case null => None
        case te => Some(te)
      }
    } else None
  }

  override def lowerTypeElement: Option[ScTypeElement] = {
    import org.jetbrains.plugins.scala.extensions._
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeAliasStub].getLowerBoundTypeElement.toOption
    }
    val tLower = findLastChildByType[PsiElement](ScalaTokenTypes.tLOWER_BOUND)
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
      def getLocationString: String = "(" + ScTypeAliasDeclarationImpl.this.containingClass.qualifiedName + ")"
      override def getIcon(open: Boolean) = ScTypeAliasDeclarationImpl.this.getIcon(0)
    }
  }

  override def getOriginalElement: PsiElement = super[ScTypeAliasDeclaration].getOriginalElement

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitTypeAliasDeclaration(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitTypeAliasDeclaration(this)
      case _ => super.accept(visitor)
    }
  }
}