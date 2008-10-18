package org.jetbrains.plugins.scala.lang.psi.impl.statements.params

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType;
import com.intellij.psi._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.base._


/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScClassParameterImpl(node: ASTNode) extends ScParameterImpl(node) with ScClassParameter {

  override def toString: String = "ClassParameter"

  // todo change me!
  override def hasModifierProperty(p: String) = false

  def isVal() = findChildByType(ScalaTokenTypes.kVAL) != null
  def isVar() = findChildByType(ScalaTokenTypes.kVAR) != null
}