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

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel._

import _root_.scala.collection.mutable._

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

class ScPackagingImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPackaging {

  override def toString = "ScPackaging"

  @NotNull
  def getPackageName: String = findChildByClass (classOf[ScReferenceElement]).getText

  @NotNull
  def getTopStatements: Array[ScTopStatement] = {
    val res = new ArrayBuffer[ScTopStatement]
    for (child <- getChildren () if child.isInstanceOf[ScTopStatement]) res += child.asInstanceOf[ScTopStatement]
    return res.toArray
  }

  def getTypeDefs = childrenOfType[ScalaPsiElementImpl] (TokenSets.TMPL_OR_TYPE_BIT_SET)

  def getInnerPackagings: Iterable[ScPackaging] = childrenOfType[ScPackaging] (TokenSets.PACKAGING_BIT_SET)

  override def getTypeDefinitions(): Seq[ScTypeDefinition] = getChildren.flatMap (collectTypeDefs)

  override def collectTypeDefs (child: PsiElement) = child match {
    case p: ScPackaging => p.getTypeDefinitions
    case t: ScTypeDefinition => List (t) ++ t.getTypeDefinitions
    case _ => Seq.empty
  }



}
