package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

import psi.stubs.elements.wrappers.DummyASTNode
import api.base._
import api.statements.params._
import api.statements._
import icons.Icons
import lang.psi.types.{ScType, Nothing}
import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util._
import psi.stubs.ScParameterStub
import toplevel.synthetic.JavaIdentifier

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScParameterImpl extends ScalaStubBasedElementImpl[ScParameter] with ScParameter {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScParameterStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "Parameter"

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER) match {
    case null => ScalaPsiElementFactory.createIdentifier(getStub.asInstanceOf[ScParameterStub].getName, getManager).getPsi
    case n => n
  }

  def paramType = findChild(classOf[ScParameterType])

  def getDeclarationScope = PsiTreeUtil.getParentOfType(this, classOf[ScParameterOwner])

  def getAnnotations = PsiAnnotation.EMPTY_ARRAY

  def getTypeElement = null

  def typeElement = paramType match {
    case Some(x) => Some(x.typeElement)
    case None => None
  }

  override def getUseScope = new LocalSearchScope(getDeclarationScope)

  def calcType() = typeElement match {
    case None => Nothing //todo inference here
    case Some(e) => e.getType
  }

  def isVarArgs = false

  def computeConstantValue = null

  def normalizeDeclaration() = false

  def hasInitializer = false

  def getInitializer = null

  def getType: PsiType = ScType.toPsi(calcType, getProject, getResolveScope)

  def getModifierList = findChildByClass(classOf[ScModifierList])

}