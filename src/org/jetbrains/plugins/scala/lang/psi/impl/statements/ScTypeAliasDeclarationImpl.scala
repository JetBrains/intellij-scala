package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import javax.swing.Icon

import com.intellij.ide.util.EditSourceUtil
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{tLOWER_BOUND, tUPPER_BOUND}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createIdentifier
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:54:54
*/

class ScTypeAliasDeclarationImpl private (stub: StubElement[ScTypeAlias], nodeType: IElementType, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScTypeAliasDeclaration {
  def this(node: ASTNode) = {this(null, null, node)}
  def this(stub: ScTypeAliasStub) = {this(stub, ScalaElementTypes.TYPE_DECLARATION, null)}

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def navigate(requestFocus: Boolean) {
    val descriptor = EditSourceUtil.getDescriptor(nameId)
    if (descriptor != null) descriptor.navigate(requestFocus)
  }

  def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER) match {
    case null => createIdentifier(getStub.asInstanceOf[ScTypeAliasStub].getName).getPsi
    case n => n
  }
  
  override def toString: String = "ScTypeAliasDeclaration: " + name

  def lowerBound: TypeResult[ScType] = lowerTypeElement match {
      case Some(te) => te.getType(TypingContext.empty)
      case None => Success(Nothing, Some(this))
  }

  def upperBound: TypeResult[ScType] = upperTypeElement match {
      case Some(te) => te.getType(TypingContext.empty)
      case None => Success(Any, Some(this))
  }

  override def upperTypeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeAliasStub].upperBoundTypeElement
    }
    Option(findLastChildByType[PsiElement](tUPPER_BOUND)).map {
      PsiTreeUtil.getNextSiblingOfType(_, classOf[ScTypeElement])
    }
  }

  override def lowerTypeElement: Option[ScTypeElement] = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTypeAliasStub].lowerBoundTypeElement
    }
    Option(findLastChildByType[PsiElement](tLOWER_BOUND)).map {
      PsiTreeUtil.getNextSiblingOfType(_, classOf[ScTypeElement])
    }
  }


  override def getPresentation: ItemPresentation = {
    new ItemPresentation() {
      def getPresentableText: String = name
      def getTextAttributesKey: TextAttributesKey = null
      def getLocationString: String = "(" + ScTypeAliasDeclarationImpl.this.containingClass.qualifiedName + ")"
      override def getIcon(open: Boolean): Icon = ScTypeAliasDeclarationImpl.this.getIcon(0)
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