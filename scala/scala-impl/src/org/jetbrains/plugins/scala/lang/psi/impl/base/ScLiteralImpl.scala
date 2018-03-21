package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import java.lang.StringBuilder
import java.util
import java.util.Random

import com.intellij.lang.ASTNode
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Pair, TextRange}
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.types.api.{Char, Int, Nothing, Null}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{api, _}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInsidePsiElement
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.project._

import scala.StringContext.InvalidEscapeException

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScLiteralImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScLiteral with ContributedReferenceHost {
  def isValidHost: Boolean = getValue.isInstanceOf[String]

  override def toString: String = "Literal"

  override def allowLiteralTypes: Boolean = getContainingFile.module.exists(_.literalTypesAllowed)

  protected override def innerType: TypeResult = {
    val wide = ScLiteralImpl.getLiteralType(getFirstChild.getNode, this)
    if (allowLiteralTypes) wide.map(ScLiteralType(getValue, _)) else wide
  }

  @CachedInsidePsiElement(this, PsiModificationTracker.MODIFICATION_COUNT)
  def getValue: AnyRef = {
    val child = getFirstChild.getNode
    var text = getText
    val textLength = getTextLength
    def getValueOfNode(e: ASTNode, isNegative: Boolean = false): AnyRef = {
      e.getElementType match {
        case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tWRONG_STRING if !isNegative =>
          if (!text.startsWith("\"")) return null
          text = text.substring(1)
          if (text.endsWith("\"")) {
            text = text.substring(0, text.length - 1)
          }
          try StringContext.treatEscapes(text) //for octal escape sequences
          catch {
            case _: InvalidEscapeException => StringUtil.unescapeStringCharacters(text)
          }
        case ScalaTokenTypes.tMULTILINE_STRING if !isNegative =>
          if (!text.startsWith("\"\"\"")) return null
          text = text.substring(3)
          if (text.endsWith("\"\"\"")) {
            text = text.substring(0, text.length - 3)
          }
          text
        case ScalaTokenTypes.kTRUE if !isNegative => java.lang.Boolean.TRUE
        case ScalaTokenTypes.kFALSE if !isNegative => java.lang.Boolean.FALSE
        case ScalaTokenTypes.tCHAR if ! isNegative =>
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
          val endsWithL = e.getText.endsWith("l") || e.getText.endsWith("L")
          text = if (endsWithL) text.substring(0, text.length - 1) else text
          val (number, base) = text match {
            case t if t.startsWith("0x") || t.startsWith("0X") => (t.substring(2), 16)
            case t if t.startsWith("0") && t.length >= 2 => (t.substring(0), 8)
            case t => (t, 10)
          }
          val limit = if (endsWithL) java.lang.Long.MAX_VALUE else java.lang.Integer.MAX_VALUE
          val divider = if (base == 10) 1 else 2
          var value = 0l
          for (d <- number.map(_.asDigit)) {
            if (value < 0 ||
              limit / (base / divider) < value / divider ||
              limit - (d / divider) < value * (base / divider)
            ) {
              return null
            }
            value = value * base + d
          }
          if (isNegative) value = -value
          if (endsWithL) java.lang.Long.valueOf(value) else Integer.valueOf(value.toInt)
        case ScalaTokenTypes.tFLOAT =>
          if (e.getText.endsWith("f") || e.getText.endsWith("F"))
            try {
              java.lang.Float.valueOf((if (isNegative) "-" else "") +text.substring(0, text.length - 1))
            } catch {
              case _: Exception => null
            }
          else
            try {
              java.lang.Double.valueOf((if (isNegative) "-" else "") + text)
            } catch {
              case _: Exception => null
            }
        case ScalaTokenTypes.tSYMBOL if !isNegative =>
          if (!text.startsWith("\'")) return null
          Symbol(text.substring(1))
        case ScalaTokenTypes.tIDENTIFIER if e.getText == "-" && !isNegative =>
          text = text.substring(1)
          getValueOfNode(e.getTreeNext, isNegative = true)
        case _ => null
      }
    }
    getValueOfNode(child)
  }

  def getInjectedPsi: util.List[Pair[PsiElement, TextRange]] = if (getValue.isInstanceOf[String]) InjectedLanguageManager.getInstance(getProject).getInjectedPsiFiles(this) else null

  def processInjectedPsi(visitor: PsiLanguageInjectionHost.InjectedPsiVisitor) {
    InjectedLanguageUtil.enumerate(this, visitor)
  }

  def updateText(text: String): ScLiteralImpl = {
    val valueNode = getNode.getFirstChildNode
    assert(valueNode.isInstanceOf[LeafElement])
    valueNode.asInstanceOf[LeafElement].replaceWithText(text)
    this
  }

  def createLiteralTextEscaper: LiteralTextEscaper[ScLiteralImpl] = if (isMultiLineString) new PassthroughLiteralEscaper(this) else new ScLiteralEscaper(this)

  def isString: Boolean = getFirstChild.getNode.getElementType match {
    case ScalaTokenTypes.tMULTILINE_STRING | ScalaTokenTypes.tSTRING => true
    case _ => false
  }

  def isMultiLineString: Boolean = getFirstChild.getNode.getElementType match {
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
    *
    * @param tp type, which should be returned by method getTypeWithouImplicits
   */
  def setTypeForNullWithoutImplicits(tp: Option[ScType]) {
    if (getFirstChild.getNode.getElementType != ScalaTokenTypes.kNULL) assert(assertion = false,
      message = "Only null literals accepted, type: " + getFirstChild.getNode.getElementType)
    typeWithoutImplicits = tp
  }

  def getTypeForNullWithoutImplicits: Option[ScType] = {
    typeWithoutImplicits
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

  def getLiteralType(node: ASTNode, element: ScalaPsiElement): TypeResult = {
    implicit val projectCtx: ProjectContext = element
    val inner = node.getElementType match {
          case ScalaTokenTypes.kNULL => Null
          case ScalaTokenTypes.tINTEGER =>
            if (node.getText.endsWith("l") || node.getText.endsWith("L")) api.Long
            else Int //but a conversion exists to narrower types in case range fits
          case ScalaTokenTypes.tFLOAT =>
            if (node.getText.endsWith("f") || node.getText.endsWith("F")) api.Float
            else api.Double
          case ScalaTokenTypes.tCHAR => Char
          case ScalaTokenTypes.tSYMBOL =>
            element.elementScope.getCachedClass("scala.Symbol")
              .map {
                ScalaType.designator
              }.getOrElse(Nothing)
          case ScalaTokenTypes.tSTRING | ScalaTokenTypes.tWRONG_STRING | ScalaTokenTypes.tMULTILINE_STRING =>
            ScalaPsiManager.instance(element.getProject)
              .getCachedClass(element.getResolveScope, "java.lang.String").map {
              ScalaType.designator
            }.getOrElse(Nothing)
          case ScalaTokenTypes.kTRUE | ScalaTokenTypes.kFALSE => api.Boolean
          case ScalaTokenTypes.tIDENTIFIER if node.getText == "-" => return getLiteralType(node.getTreeNext, element)
          case _ => return Failure("Wrong Psi to get Literal type")
        }
    Right(inner)
  }
}
