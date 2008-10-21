package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiModifierList}
import stubs.elements.wrappers.DummyASTNode
import stubs.ScTypeDefinitionStub
import api.base.ScModifierList

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
 * @author Alexander.Podkhalyuzin
 */

class ScClassImpl extends ScTypeDefinitionImpl with ScClass with ScTypeParametersOwner with ScTemplateDefinition {
 def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTypeDefinitionStub) = {this(); setStub(stub)}

  override def toString: String = "ScClass"

  override def getIconInner = Icons.CLASS

  override def getModifierList: ScModifierList = findChildByClass(classOf[ScModifierList])

  def parameters = constructor match {
    case Some(c) => c.parameters
    case None => Seq.empty
  }

  override def members() = constructor match {
    case Some(c) => super.members ++ Seq.singleton(c)
    case _ => super.members
  }

  import com.intellij.psi.{scope, PsiElement, ResolveState}
  import scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (!super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place)) return false

    return super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
  }

  def isCase = getModifierList.has(ScalaTokenTypes.kCASE)
}