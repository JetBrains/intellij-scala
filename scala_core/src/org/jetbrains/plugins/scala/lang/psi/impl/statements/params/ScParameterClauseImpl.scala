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


/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScParameterClauseImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParameterClause {

  override def toString: String = "ParametersClause"

  def getParameters: Seq[ScParameter] = List.fromArray(findChildrenByClass(classOf[ScParameter]))

  def getParametersAsString: String = {
    val res = new StringBuffer("");
    for (param <- getParameters) {
      if (param.paramType != null)
        res.append(param.paramType.getText())
      else
        res.append("AnyRef")
      res.append(", ")
    }
    if (res.length >= 2)
      res.delete(res.length - 2, res.length)
    return res.toString
  }
}