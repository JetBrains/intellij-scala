package org.jetbrains.plugins.scala.lang.psi.impl.expr

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl






import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import types.{Bounds, ScType};
import com.intellij.psi._

import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons


import org.jetbrains.plugins.scala.lang.psi.api.expr._

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScTryStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTryStmt {
  override def toString: String = "TryStatement"


  override def getType(): ScType = {
    var result = tryBlock.getType
    catchBlock match {
      case Some(catchBlock) =>
        catchBlock.getBranches.foldLeft(tryBlock.getType: ScType)((t, b) => Bounds.lub(t, b.getType))
      case None => tryBlock.getType
    }
  }
}