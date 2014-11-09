package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import java.lang.StringBuilder
import java.util.Random

import com.intellij.lang.ASTNode
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}

import scala.StringContext.InvalidEscapeException

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScLiteralImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScLiteral with ContributedReferenceHost {
  def isValidHost: Boolean = getValue.isInstanceOf[String]

  override def toString: String = "Literal"

  protected override def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val child = getFirstChild.getNode
    val inner = child.getElementType match {
      case ScalaTokenTypes.kNULL => Null
      case ScalaTokenTypes.tINTEGER =>
        if (child.getText.endsWith('l') || child.getText.endsWith('L')) Long
        else Int //but a conversion exists to narrower types in case range fits
      case ScalaTokenTypes.tFLOAT =>
        if (child.getText.endsWith('f') || child.getText.endsWith('F')) Float
        else Double
      case ScalaTokenTypes.tCHAR => Char
      case ScalaTokenTypes.tSYMBOL =>
        val sym = ScalaPsiManager.instance(getProject).getCachedClass("scala.Symbol", getResolveScope,
          ScalaPsiManager.ClassCategory.TYPE)
        if (sym != null) ScType.designator(sym) else Nothing
      case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tWRONG_STRING | ScalaTokenTypes.tMULTILINE_STRING =>
        val str = ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "java.lang.String")
        if (str != null) ScType.designator(str) else Nothing
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
      case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tWRONG_STRING =>
        if (!text.startsWith('"')) return null
        text = text.substring(1)
        if (text.endsWith('"')) {
          text = text.substring(0, text.length - 1)
        }
        try StringContext.treatEscapes(text) //for octal escape sequences
        catch {
          case e: InvalidEscapeException => StringUtil.unescapeStringCharacters(text)
        }
      case ScalaTokenTypes.tMULTILINE_STRING =>
        if (!text.startsWith("\"\"\"")) return null
        text = text.substring(3)
        if (text.endsWith("\"\"\"")) {
          text = text.substring(0, text.length - 3)
        }
        text
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
            case e: Exception => null
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
            case e: Exception => null
          }
        }
      case ScalaTokenTypes.tFLOAT =>
        if (child.getText.endsWith('f') || child.getText.endsWith('F'))
          try {
            java.lang.Float.valueOf(text.substring(0, text.length - 1))
          } catch {
            case e: Exception => null
          }
        else
          try {
            java.lang.Double.valueOf(text)
          } catch {
            case e: Exception => null
          }
      case ScalaTokenTypes.tSYMBOL =>
        if (!text.startsWith('\'')) return null
        Symbol(text.substring(1))
      case _ => null
    }
  }

  def getInjectedPsi = if (getValue.isInstanceOf[String]) InjectedLanguageManager.getInstance(getProject).getInjectedPsiFiles(this) else null

  def processInjectedPsi(visitor: PsiLanguageInjectionHost.InjectedPsiVisitor) {
    InjectedLanguageUtil.enumerate(this, visitor)
  }

  def updateText(text: String)  = {
    val valueNode = getNode.getFirstChildNode
    assert(valueNode.isInstanceOf[LeafElement])
    valueNode.asInstanceOf[LeafElement].replaceWithText(text)
    this
  }

  def createLiteralTextEscaper = if (isMultiLineString) new PassthroughLiteralEscaper(this) else new ScLiteralEscaper(this)

  def isString = getFirstChild.getNode.getElementType match {
    case ScalaTokenTypes.tMULTILINE_STRING | ScalaTokenTypes.tSTRING => true
    case _ => false
  }

  def isMultiLineString = getFirstChild.getNode.getElementType match {
    case ScalaTokenTypes.tMULTILINE_STRING => true
    case _ => false
  }

  override def isSymbol: Boolean = getFirstChild.getNode.getElementType == ScalaTokenTypes.tSYMBOL

  override def isChar: Boolean = getFirstChild.getNode.getElementType == ScalaTokenTypes.tCHAR

  override def getReferences: Array[PsiReference] = {
    PsiReferenceService.getService.getContributedReferences(this)
  }

  def contentRange: TextRange = {
    val range = getTextRange
    if (isString) {
      val quote = if (isMultiLineString) "\"\"\"" else "\""
      val prefix = this match {
        case intrp: ScInterpolatedStringLiteral => intrp.reference.fold("")(_.refName)
        case _ => ""
      }
      new TextRange(range.getStartOffset + prefix.length + quote.length, range.getEndOffset - quote.length)
    }
    else if (isChar) {
      new TextRange(range.getStartOffset + 1, range.getEndOffset - 1)
    }
    else if (isSymbol) {
      new TextRange(range.getStartOffset + 1, range.getEndOffset)
    }
    else range
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitLiteral(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitLiteral(this)
      case _ => super.accept(visitor)
    }
  }

  @volatile
  private var typeWithoutImplicits: Option[ScType] = None

  /**
   * This method works only for null literal (to avoid possibly dangerous usage)
   * @param tp type, which should be returned by method getTypeWithouImplicits
   */
  def setTypeWithoutImplicits(tp: Option[ScType]) {
    if (getFirstChild.getNode.getElementType != ScalaTokenTypes.kNULL) assert(assertion = false,
      message = "Only null literals accepted, type: " + getFirstChild.getNode.getElementType)
    typeWithoutImplicits = tp
  }

  override def getTypeWithoutImplicits(ctx: TypingContext, ignoreBaseTypes: Boolean, fromUnderscore: Boolean): TypeResult[ScType] = {
    val tp = typeWithoutImplicits
    if (tp != None) return Success(tp.get, None)
    super.getTypeWithoutImplicits(ctx, ignoreBaseTypes, fromUnderscore)
  }
  
  /*
   * This part caches literal related annotation owners
   * todo: think about extracting this feature to a trait  
   * 
   * trait AnnotationBasedInjectionHost {
   *   private[this] var myAnnotationOwner: Option[PsiAnnotationOwner] = None
   *   ...
   *   private val expTimeLengthGenerator = if (needCaching()) new Random(System.currentTimeMillis()) else null
   *   ...
   *   ...
   *   def needCaching(): Boolean
   * }
   */
  
  private[this] var myAnnotationOwner: Option[PsiAnnotationOwner with PsiElement] = None
  private[this] var expirationTime = 0L
  
  private val expTimeLengthGenerator: Random = new Random(System.currentTimeMillis()) 
  
  
  def getAnnotationOwner(annotationOwnerLookUp: ScLiteral => Option[PsiAnnotationOwner with PsiElement]): Option[PsiAnnotationOwner] = {
    if (!isString) return None
    
    if (System.currentTimeMillis() > expirationTime || myAnnotationOwner.exists(!_.isValid)) {
      myAnnotationOwner = annotationOwnerLookUp(this)
      expirationTime = System.currentTimeMillis() + (2 + expTimeLengthGenerator.nextInt(8))*1000
    }
    
    myAnnotationOwner
  }
}

object ScLiteralImpl {
  object string {
    def unapply(lit: ScLiteralImpl): Option[String] =
      if (lit.isString) Some(lit.getValue.asInstanceOf[String]) else None
  }
}