package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import lexer.ScalaTokenTypes
import psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import psi.types._
import result.{TypeResult, Failure, Success, TypingContext}
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import java.lang.{StringBuilder, String}
import api.base.ScLiteral
import com.intellij.psi._
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.extensions._
import com.intellij.openapi.extensions.Extensions

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScLiteralImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScLiteral with ContributedReferenceHost {
  override def toString: String = "Literal"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val child = getFirstChild.getNode
    val inner = child.getElementType match {
      case ScalaTokenTypes.kNULL => Null
      case ScalaTokenTypes.tINTEGER => {
        if (child.getText.endsWith('l') || child.getText.endsWith('L')) Long
        else Int //but a conversion exists to narrower types in case range fits
      }
      case ScalaTokenTypes.tFLOAT => {
        if (child.getText.endsWith('f') || child.getText.endsWith('F')) Float
        else Double
      }
      case ScalaTokenTypes.tCHAR => Char
      case ScalaTokenTypes.tSYMBOL => {
        val sym = JavaPsiFacade.getInstance(getProject).findClass("scala.Symbol", getResolveScope)
        if (sym != null) ScType.designator(sym) else Nothing
      }
      case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tWRONG_STRING | ScalaTokenTypes.tMULTILINE_STRING => {
        val str = JavaPsiFacade.getInstance(getProject).findClass("java.lang.String", getResolveScope)
        if (str != null) ScType.designator(str) else Nothing
      }
      case ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE => Boolean
      case _ => return Failure("Wrong Psi to get Literal type", Some(this))
    }
    Success(inner, Some(this))
  }

  //TODO complete the implementation
  def getValue: AnyRef = {
    val child = getFirstChild.getNode
    var text = getText
    val textLength = getTextLength
    child.getElementType match {
      case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tWRONG_STRING => {
        if (!text.startsWith('"')) return null
        text = text.substring(1)
        if (text.endsWith('"')) {
          text = text.substring(0, text.length - 1)
        }
        StringUtil.unescapeStringCharacters(text)
      }
      case ScalaTokenTypes.tMULTILINE_STRING => {
        if (!text.startsWith("\"\"\"")) return null
        text = text.substring(3)
        if (text.endsWith("\"\"\"")) {
          text = text.substring(0, text.length - 3)
        }
        text
      }
      case ScalaTokenTypes.kTRUE => java.lang.Boolean.TRUE
      case ScalaTokenTypes.kFALSE => java.lang.Boolean.FALSE
      case ScalaTokenTypes.tCHAR =>
        if (StringUtil.endsWithChar(getText, '\'')) {
          if (textLength == 1) return null
          text = text.substring(1, textLength - 1)
        }
        else {
          text = text.substring(1, textLength)
        }
        val chars: StringBuilder = new StringBuilder
        val success: Boolean = PsiLiteralExpressionImpl.parseStringCharacters(text, chars, null)
        if (!success) return null
        if (chars.length != 1) return null
        Character.valueOf(chars.charAt(0))
      case ScalaTokenTypes.tINTEGER =>
        if (child.getText.endsWith('l') || child.getText.endsWith('L'))
          try {
            java.lang.Long.valueOf(text.substring(0, text.length - 1))
          } catch {
            case e => null
          }
        else {
          try {
            if (text.startsWith("0x")) {
              Integer.valueOf(java.lang.Integer.parseInt(text.substring(2), 16))
            } else if (text.startsWith('0')) {
              Integer.valueOf(Integer.parseInt(text, 8))
            } else {
              Integer.valueOf(text)
            }
          } catch {
            case e => null
          }
        }
      case ScalaTokenTypes.tFLOAT =>
        if (child.getText.endsWith('f') || child.getText.endsWith('F'))
          try {
            java.lang.Float.valueOf(text.substring(0, text.length - 1))
          } catch {
            case e => null
          }
        else
          try {
            java.lang.Double.valueOf(text)
          } catch {
            case e => null
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

  def createLiteralTextEscaper = {
    if (getFirstChild.getNode.getElementType == ScalaTokenTypes.tMULTILINE_STRING)
      new PassthroughLiteralEscaper(this)
    else
      new ScLiteralEscaper(this)
  }

  def isString = getFirstChild.getNode.getElementType match {
    case ScalaTokenTypes.tMULTILINE_STRING | ScalaTokenTypes.tSTRING => true
    case _ => false
  }

  def isMultiLineString = getFirstChild.getNode.getElementType match {
    case ScalaTokenTypes.tMULTILINE_STRING => true
    case _ => false
  }

  @NotNull override def getReferences: Array[PsiReference] = {
    PsiReferenceService.getService.getContributedReferences(this)
  }
}