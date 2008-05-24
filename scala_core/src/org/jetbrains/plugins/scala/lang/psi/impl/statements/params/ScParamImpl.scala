package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import com.intellij.psi.util._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.statements._


/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScParameterImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParameter {

  override def toString: String = "Parameter"

  def getNameIdentifier = null

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def getName = nameId.getText

  def paramType = findChild(classOf[ScParameterType])

  def getDeclarationScope = PsiTreeUtil.getParentOfType(this, classOf[ScParameterOwner])

  def getAnnotations = PsiAnnotation.EMPTY_ARRAY

  def getTypeElement = null

  def typeElement = paramType match {
    case Some(x) => Some(x.typeElement)
    case None => None
  }

  // todo implement me!
  def isVarArgs = false

  def computeConstantValue = null

  def normalizeDeclaration() = false

  def hasInitializer = false

  def getInitializer = null

  def getType: PsiType = PsiType.INT

  def getModifierList = findChildByClass(classOf[ScModifierList])

}