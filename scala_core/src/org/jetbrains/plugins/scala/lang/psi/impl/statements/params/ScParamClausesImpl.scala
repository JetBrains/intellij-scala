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
import com.intellij.psi.PsiElement


/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScParamClausesImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParamClauses {

  override def toString: String = "ParametersClauses"

  def params: Seq[ScParameter] = {
    getChildren.flatMap((child: PsiElement) =>
            child match {
              case e: ScParamClause => e.getParameters
              case _ => Seq.empty
            }
    )
  }

  def getParametersAsString: String = {
    val res: StringBuffer = new StringBuffer("")
    for (child <- getChildren) {
      child match {
        case e: ScParamClause => {
          res.append("(")
          res.append(e.getParametersAsString)
          res.append(")")
        }
        case _ =>
      }
    }
    return res.toString()
  }

  def getParameterIndex(p: PsiParameter) = params.indexOf(List(p))

  def getParametersCount = params.length

}