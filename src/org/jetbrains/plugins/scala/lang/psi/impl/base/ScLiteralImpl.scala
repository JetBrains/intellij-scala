package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import api.base.ScLiteral
import psi.types._
import result.{TypeResult, Failure, Success, TypingContext}
import java.lang.String
import com.intellij.psi.{PsiLanguageInjectionHost, JavaPsiFacade}
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.openapi.util.text.StringUtil

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScLiteralImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScLiteral{
  override def toString: String = "Literal"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
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
      case _ => return Failure("Wrong Psi to get Literal type", Some(this))
    }
    Success(inner, Some(this))
  }

  //TODO complete the implementation
  def getValue = {
    val child = getFirstChild.getNode
    child.getElementType match {
      case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tWRONG_STRING | ScalaTokenTypes.tMULTILINE_STRING => {
        StringUtil.unescapeStringCharacters(child.getText)
      }
      case _ => null
    }
  }

  def getInjectedPsi = if (getValue.isInstanceOf[String]) InjectedLanguageUtil.getInjectedPsiFiles(this) else null

  def processInjectedPsi(visitor: PsiLanguageInjectionHost.InjectedPsiVisitor) {
    InjectedLanguageUtil.enumerate(this, visitor)
  }

  def updateText(text: String)  = {
    val valueNode = getNode.getFirstChildNode
    assert(valueNode.isInstanceOf[LeafElement])
    (valueNode.asInstanceOf[LeafElement]).replaceWithText(text)
    this
  }

  def createLiteralTextEscaper = new ScLiteralEscaper(this)
}