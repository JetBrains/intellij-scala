package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.stubs.IStubElementType
import stubs.ScTypeDefinitionStub
import api.base.ScModifierList
import com.intellij.psi.{PsiElement, PsiModifierList}
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

/**
 * @autor Alexander.Podkhalyuzin
 */

class ScClassImpl(node: ASTNode) extends ScTypeDefinitionImpl(node) with ScClass with ScTypeParametersOwner {

  override def toString: String = "ScClass"

  override def getIconInner = Icons.CLASS

  override def getModifierList: ScModifierList = findChildByClass(classOf[ScModifierList])

  def parameters = constructor match {
    case Some(c) => c.parameters.params
    case None => Seq.empty
  }

  import com.intellij.psi.{scope, PsiElement, ResolveState}
  import scope.PsiScopeProcessor

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    for (p <- parameters) {
      if (!processor.execute(p, state)) return false
    }
    if (!super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place)) return false
    return super.processDeclarations(processor, state, lastParent, place)
  }

  def isCase = getModifierList.has(ScalaTokenTypes.kCASE)
}

object ScClassImpl {
  def apply(stub: ScTypeDefinitionStub) = new ScClassImpl(null) {
    setStub(stub.asInstanceOf[Nothing])
    override def getElementType = ScalaElementTypes.CLASS_DEF.asInstanceOf[IStubElementType[Nothing, Nothing]]
  }
}