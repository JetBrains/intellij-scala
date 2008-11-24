package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, PsiModifier, ResolveState}
import stubs.elements.wrappers.DummyASTNode
import stubs.ScTypeDefinitionStub
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.tree.IElementType
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList

/**
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScObjectImpl extends ScTypeDefinitionImpl with ScObject with ScTemplateDefinition{
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTypeDefinitionStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScObject"

  override def getIconInner = Icons.OBJECT

  //todo refactor
  override def getModifierList = findChildByClass(classOf[ScModifierList])

  override def hasModifierProperty(name: String): Boolean = {
    if (getModifierList != null) {
      if (name == PsiModifier.PUBLIC) {
        val list = getModifierList
        return !list.has(ScalaTokenTypes.kPRIVATE) && !list.has(ScalaTokenTypes.kPROTECTED)
      }
      getModifierList.hasModifierProperty(name: String)
    }
    else false
  }

  override def getContainingClass() = null

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean =
    super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
}