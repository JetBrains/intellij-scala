package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports

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

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScImportSelectorsImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScImportSelectors{
  override def toString: String = "ImportSelectors"

  def hasWildcard = findChildByType(ScalaTokenTypes.tUNDER) != null

  def wildcardElement: Option[PsiElement] = {
    if (hasWildcard) Some(findChildByType(ScalaTokenTypes.tUNDER))
    else None
  }
}