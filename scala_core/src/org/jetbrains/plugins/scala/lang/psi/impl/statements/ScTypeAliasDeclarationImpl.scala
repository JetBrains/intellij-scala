package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl






import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._

import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{Nothing, Any}
import org.jetbrains.plugins.scala.icons.Icons


import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import com.intellij.psi.util.PsiTreeUtil

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:54:54
*/

class ScTypeAliasDeclarationImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeAliasDeclaration {
  def nameId() = findChildByType(ScalaTokenTypes.tIDENTIFIER)
  
  override def toString: String = "ScTypeAliasDeclaration"

  override def getModifierList: ScModifierList = null

  def lowerBound = {
    val tLower = findChildByType(ScalaTokenTypes.tLOWER_BOUND)
    if (tLower != null) {
      PsiTreeUtil.getNextSiblingOfType(tLower, classOf[ScTypeElement]) match {
        case null => Nothing
        case te => te.getType
      }
    } else Nothing
  }

  def upperBound = {
    val tUpper = findChildByType(ScalaTokenTypes.tUPPER_BOUND)
    if (tUpper != null) {
      PsiTreeUtil.getNextSiblingOfType(tUpper, classOf[ScTypeElement]) match {
        case null => Any
        case te => te.getType
      }
    } else Any
  }

  def isDeprecated = false

  def getDocComment: PsiDocComment = null
}