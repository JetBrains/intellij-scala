package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.JavaPsiFacade
import api.base.ScLiteral
import psi.types._
import result.{Success, TypingContext}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScLiteralImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScLiteral{
  override def toString: String = "Literal"

  protected override def innerType(ctx: TypingContext) = {
    val child = getFirstChild.getNode
    val inner = child.getElementType match {
      case ScalaTokenTypes.kNULL => Null
      case ScalaTokenTypes.tINTEGER => {
        if (child.getText.endsWith("l") || child.getText.endsWith("L")) Long
        // todo use TypingContext to put context-specific info
        else Int //but a conversion exists to narrower types in case range fits
      }
      case ScalaTokenTypes.tFLOAT => {
        if (child.getText.endsWith("f") || child.getText.endsWith("F")) Float
        else Double
      }
      case ScalaTokenTypes.tCHAR => Char
      case ScalaTokenTypes.tSYMBOL => {
        val sym = JavaPsiFacade.getInstance(getProject).findClass("scala.Symbol", getResolveScope)
        if (sym != null) new ScDesignatorType(sym) else Nothing
      }
      case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tWRONG_STRING | ScalaTokenTypes.tMULTILINE_STRING => {
        val str = JavaPsiFacade.getInstance(getProject).findClass("java.lang.String", getResolveScope)
        if (str != null) new ScDesignatorType(str) else Nothing
      }
      case ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE => Boolean
    }
    Success(inner, Some(this))
  }
}