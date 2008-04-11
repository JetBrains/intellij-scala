package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.packaging

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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.base._

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScPackageStatementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPackageStatement{
  override def toString = "ScPackageStatement"

  @NotNull
  def getPackageName: String = findChildByClass(classOf[ScReferenceElement]).getText
}