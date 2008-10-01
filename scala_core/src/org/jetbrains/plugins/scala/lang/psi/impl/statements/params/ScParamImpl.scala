package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import lang.psi.types.{ScType, Nothing}
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