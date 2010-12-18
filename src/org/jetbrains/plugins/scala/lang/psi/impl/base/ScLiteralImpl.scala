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
import api.statements.{ScVariableDefinition, ScAnnotationsHolder, ScPatternDefinition}
import api.base.{ScReferenceElement, ScLiteral}
import api.expr.{ScArgumentExprList, ScMethodCall, ScAssignStmt, ScAnnotation}
import api.base.patterns.ScReferencePattern
import com.intellij.psi.{PsiAnnotationOwner, PsiElement, PsiLanguageInjectionHost, JavaPsiFacade}

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
  def getValue: AnyRef = {
    val child = getFirstChild.getNode
    var text = getText
    val textLength = getTextLength
    child.getElementType match {
      case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tWRONG_STRING => {
        if (!text.startsWith("\"")) return null
        text = text.substring(1)
        if (text.endsWith("\"")) {
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
        return Character.valueOf(chars.charAt(0))
      case ScalaTokenTypes.tINTEGER =>
        if (child.getText.endsWith("l") || child.getText.endsWith("L"))
          try {
            java.lang.Long.valueOf(text)
          } catch {
            case e => null
          }
        else {
          try {
            java.lang.Integer.valueOf(text)
          } catch {
            case e => null
          }
        }
      case ScalaTokenTypes.tFLOAT =>
        if (child.getText.endsWith("f") || child.getText.endsWith("F"))
          try {
            java.lang.Float.valueOf(text)
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

  def annotatedLanguageId(languageAnnotationName: String): Option[String] = {
    getParent match {
      case e: ScPatternDefinition => extractLanguage(e, languageAnnotationName)
      case e: ScVariableDefinition => extractLanguage(e, languageAnnotationName)
      case assignment: ScAssignStmt => {
        if (assignment.getContext.isInstanceOf[ScArgumentExprList]) return None// named argument

        val l = assignment.getLExpression

        if (l.isInstanceOf[ScMethodCall]) return None // map(x) = y

        l.asOptionOf(classOf[ScReferenceElement])
                .flatMap(_.resolve.toOption)
                .map(contextOf)
                .flatMap(_.asOptionOf(classOf[PsiAnnotationOwner]))
                .flatMap(it => extractLanguage(it, languageAnnotationName))
      }
      case _ => None
    }
  }

  private def contextOf(element: PsiElement) = element match {
    case p: ScReferencePattern => p.getParent.getParent
    case _ => element
  }

  private def extractLanguage(element: PsiAnnotationOwner, languageAnnotationName: String) = {
    element.getAnnotations
            .find(_.getQualifiedName == languageAnnotationName)
            .flatMap(_.asInstanceOf[ScAnnotation].constructor.args)
            .flatMap(_.children.findByType(classOf[ScLiteral]))
            .flatMap(_.getValue.asOptionOf(classOf[String]))
            .headOption
  }
}