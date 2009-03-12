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

class ScMatchStmtImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMatchStmt {
  override def toString: String = "MatchStatement"

  override def getType(): ScType = {
    //todo: it's wrong now (bounds)
    var result: ScType = null
    getBranches.length match {
      case 0 => types.Nothing
      case 1 => getBranches(0).getType
      case _ => {
        result = getBranches(0).getType
        for (i <- 1 to getBranches.length - 1) {
          result = Bounds.lub(result, getBranches(i).getType)
        }
        result
      }
    }
  }
}