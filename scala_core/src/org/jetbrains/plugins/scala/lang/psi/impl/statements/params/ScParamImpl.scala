package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

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
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScParameterImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParameter {

  override def toString: String = "Parameter"

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  def getNameIdentifier = null

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)

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

  // todo implement me!
  def isVarArgs = false

  def computeConstantValue = null

  def normalizeDeclaration() = false

  def hasInitializer = false

  def getInitializer = null

  def getType: PsiType = ScType.toPsi(calcType, getProject, getResolveScope)

  def getModifierList = findChildByClass(classOf[ScModifierList])

}