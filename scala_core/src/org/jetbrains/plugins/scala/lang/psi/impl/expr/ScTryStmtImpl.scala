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
    //todo: it's wrong for now (wrong bounds)
    //todo: add tests
    //bound catch block, all case braches under catch block, finally should be ignored
    var result = tryBlock.getType
    catchBlock match {
      case Some(catchBlock) => for (i <- 0 to catchBlock.getBranches.length - 1) {
        result = Bounds.lub(result, catchBlock.getBranches(i).getType)
      }
      case None =>
    }
    result
  }
}