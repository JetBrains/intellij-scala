package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.ide.util.EditSourceUtil
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:55:13
*/
class ScTypeAliasDefinitionImpl extends ScalaStubBasedElementImpl[ScTypeAlias] with ScTypeAliasDefinition {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTypeAliasStub) = {this(); setStub(stub); setNode(null)}

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case null =>
      val name = getStub.asInstanceOf[ScTypeAliasStub].getName
      val id = ScalaPsiElementFactory.createIdentifier(name, getManager)
      if (id == null) {
        assert(assertion = false, s"Id is null. Name: $name. Text: $getText. Parent text: ${getParent.getText}.")
      }
      id.getPsi
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
