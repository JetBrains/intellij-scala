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
import stubs.ScTypeAliasStub;
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import api.ScalaElementVisitor
import psi.types.{ScTypeParameterType, ScType, Equivalence, ScParameterizedType}
import api.toplevel.ScTypeParametersOwner
import api.statements.params.ScTypeParam
import api.toplevel.typedef.{ScMember, ScObject, ScTrait, ScClass}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:55:13
*/
class ScTypeAliasDefinitionImpl extends ScalaStubBasedElementImpl[ScTypeAlias] with ScTypeAliasDefinition {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTypeAliasStub) = {this(); setStub(stub); setNode(null)}

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case null => ScalaPsiElementFactory.createIdentifier(getStub.asInstanceOf[ScTypeAliasStub].getName, getManager).getPsi
    case n => n
  }

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def navigate(requestFocus: Boolean) {
    val descriptor =  EditSourceUtil.getDescriptor(nameId);
    if (descriptor != null) descriptor.navigate(requestFocus)
  }

  override def toString: String = "ScTypeAliasDefinition: " + name

  override def getPresentation: ItemPresentation = {
    new ItemPresentation() {
      def getPresentableText = name
      def getTextAttributesKey: TextAttributesKey = null
      def getLocationString: String = "(" + ScTypeAliasDefinitionImpl.this.containingClass.qualifiedName + ")"
      override def getIcon(open: Boolean) = ScTypeAliasDefinitionImpl.this.getIcon(0)
    }
  }

  override def getOriginalElement: PsiElement = super[ScTypeAliasDefinition].getOriginalElement

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitTypeAliasDefinition(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitTypeAliasDefinition(this)
      case _ => super.accept(visitor)
    }
  }
}
