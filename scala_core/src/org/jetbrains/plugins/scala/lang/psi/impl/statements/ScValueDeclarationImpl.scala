package org.jetbrains.plugins.scala.lang.psi.impl.statements

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


import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:55:28
*/

class ScValueDeclarationImpl(node: ASTNode) extends ScMemberImpl(node) with ScValueDeclaration{
  override def toString: String = "ScValueDeclaration"

  override def getIcon(flags: Int) = Icons.VAL

  def ids: Seq[ScFieldId] = getIdList.fieldIds

  def names = ids map (_.name)
}