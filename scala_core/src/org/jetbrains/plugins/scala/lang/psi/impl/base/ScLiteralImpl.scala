package org.jetbrains.plugins.scala.lang.psi.impl.base

import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.JavaPsiFacade
import api.base.ScLiteral
import psi.types._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScLiteralImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScLiteral{
  override def toString: String = "Literal"

  override def getType() = {
    getFirstChild.getNode.getElementType match {
      case ScalaTokenTypes.kNULL => Null
      case ScalaTokenTypes.tINTEGER => Int  //but a conversion exists to narrower types in case range fits
      case ScalaTokenTypes.tFLOAT => Double
      case ScalaTokenTypes.tCHAR => Char
      case ScalaTokenTypes.tSYMBOL => {
        val sym = JavaPsiFacade.getInstance(getProject).findClass("scala.Symbol", getResolveScope)
        if (sym != null) new ScDesignatorType(sym) else Nothing
      }
      case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tWRONG_STRING => {
        val str = JavaPsiFacade.getInstance(getProject).findClass("java.lang.String", getResolveScope)
        if (str != null) new ScDesignatorType(str) else Nothing
      }
    }
  }
}