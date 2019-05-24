package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import java.{lang => jl, util => ju}

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.{LeafElement, java}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */
class ScLiteralImpl(node: ASTNode) extends expr.ScExpressionImplBase(node)
  with ScLiteral with ContributedReferenceHost {

  import ScLiteralImpl._
  import lang.lexer.{ScalaTokenTypes => T}

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

  private[this] var myAnnotationOwner = Option.empty[PsiAnnotationOwner with PsiElement]
  private[this] var expirationTime = 0L

  @volatile
  private[this] var typeForNullWithoutImplicits_ = Option.empty[ScType]

  override def typeForNullWithoutImplicits_=(`type`: Option[ScType]): Unit = {
    if (firstElementType != T.kNULL)
      throw new jl.AssertionError(s"Only null literals accepted, type: $firstElementType")
    typeForNullWithoutImplicits_ = `type`
  }

  override def typeForNullWithoutImplicits: Option[ScType] = typeForNullWithoutImplicits_

  def isValidHost: Boolean = getValue.isInstanceOf[String]

  override def toString: String = "Literal"

  protected override def innerType: result.TypeResult =
    ScLiteralType.inferType(this)

  @CachedInUserData(this, util.PsiModificationTracker.MODIFICATION_COUNT)
  def getValue: AnyRef = {
    def getValueOfNode(node: ASTNode,
                       text: String,
                       isNegative: Boolean = false): AnyRef = node.getElementType match {
      case T.tSTRING |
           T.tWRONG_STRING if !isNegative =>
        trimQuotes(text, SingleLineQuote)() match {
          case null => null
          case stringText =>
            try {
              StringContext.treatEscapes(stringText) // for octal escape sequences
            } catch {
              case _: StringContext.InvalidEscapeException => StringUtil.unescapeStringCharacters(text)
            }
        }
      case T.tMULTILINE_STRING if !isNegative =>
        trimQuotes(text, MultiLineQuote)()
      case T.kTRUE if !isNegative => jl.Boolean.TRUE
      case T.kFALSE if !isNegative => jl.Boolean.FALSE
      case T.tCHAR if !isNegative =>
        text match {
          case CharQuote => null
          case _ =>
            val chars = new jl.StringBuilder
            val success = java.PsiLiteralExpressionImpl.parseStringCharacters(
              trimQuotes(text, CharQuote)(),
              chars,
              null
            )

            if (success && chars.length == 1) Character.valueOf(chars.charAt(0))
            else null
        }
      case T.tINTEGER =>
        val isLong = node.getText.matches(".*[lL]$")
        val numberText = text.substring(
          0,
          text.length - (if (isLong) 1 else 0)
        )

        val (number, base) = numberText match {
          case t if t.startsWith("0x") || t.startsWith("0X") => (t.substring(2), 16)
          case t if t.startsWith("0") && t.length >= 2 => (t.substring(0), 8)
          case t => (t, 10)
        }

        val limit = if (isLong) jl.Long.MAX_VALUE else jl.Integer.MAX_VALUE
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

        if (isLong) jl.Long.valueOf(value)
        else Integer.valueOf(value.toInt)
      case T.tFLOAT =>
        val isFloat = node.getText.matches(".*[fF]$")
        val numberText = text.substring(
          0,
          text.length - (if (isFloat) 1 else 0)
        )

        try {
          val number = (if (isNegative) "-" else "") + numberText
          if (isFloat) jl.Float.valueOf(number)
          else jl.Double.valueOf(number)
        } catch {
          case _: NumberFormatException => null
        }
      case T.tSYMBOL if !isNegative =>
        trimQuotes(text, CharQuote)("") match {
          case null => null
          case symbolText => Symbol(symbolText)
        }
      case T.tIDENTIFIER if node.getText == "-" && !isNegative =>
        getValueOfNode(
          node.getTreeNext,
          text.substring(1),
          isNegative = true
        )
      case _ => null
    }

    getValueOfNode(getFirstChild.getNode, getText)
  }

  def updateText(text: String): ScLiteralImpl = {
    getNode.getFirstChildNode match {
      case leaf: LeafElement => leaf.replaceWithText(text)
    }
    this
  }

  def createLiteralTextEscaper: LiteralTextEscaper[ScLiteralImpl] =
    if (isMultiLineString) new PassthroughLiteralEscaper(this)
    else new ScLiteralEscaper(this)

  private def firstElementType: IElementType = getFirstChild.getNode.getElementType

  override def isString: Boolean = firstElementType match {
    case T.tMULTILINE_STRING | T.tSTRING => true
    case _ => false
  }

  override def isMultiLineString: Boolean = firstElementType == T.tMULTILINE_STRING

  override def isSymbol: Boolean = firstElementType == T.tSYMBOL

  override def isChar: Boolean = firstElementType == T.tCHAR

  override def getReferences: Array[PsiReference] = PsiReferenceService.getService.getContributedReferences(this)

  override def contentRange: TextRange = {
    val maybeShifts = firstElementType match {
      case T.tSTRING => stringShifts(SingleLineQuote)
      case T.tMULTILINE_STRING => stringShifts(MultiLineQuote)
      case T.tCHAR => Some(1, 1)
      case T.tSYMBOL => Some(1, 0)
      case _ => None
    }

    val range = getTextRange
    maybeShifts.fold(range) {
      case (shiftStart, shiftEnd) => new TextRange(
        range.getStartOffset + shiftStart,
        range.getEndOffset - shiftEnd
      )
    }
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitLiteral(this)
  }

  def getAnnotationOwner(annotationOwnerLookUp: ScLiteral => Option[PsiAnnotationOwner with PsiElement]): Option[PsiAnnotationOwner] = {
    if (!isString) return None

    System.currentTimeMillis match {
      case currentTimeMillis if currentTimeMillis <= expirationTime && myAnnotationOwner.forall(_.isValid) =>
      case currentTimeMillis =>
        myAnnotationOwner = annotationOwnerLookUp(this)
        expirationTime = currentTimeMillis + (2 + ExpTimeLengthGenerator.nextInt(8)) * 1000
    }

    myAnnotationOwner
  }
}

object ScLiteralImpl {

  private val ExpTimeLengthGenerator = new ju.Random(System.currentTimeMillis)

  private[base] val CharQuote = "\'"
  private[base] val SingleLineQuote = "\""
  private[base] val MultiLineQuote = "\"\"\""

  object string {

    @deprecated("use `ScLiteral.StringValue` instead")
    def unapply(lit: ScLiteralImpl): Option[String] =
      if (lit.isString) Some(lit.getValue.asInstanceOf[String]) else None
  }

  private[base] def stringShifts(quote: String): Some[(Int, Int)] = {
    val quoteLength = quote.length
    Some(quoteLength, quoteLength)
  }

  private def trimQuotes(text: String, startQuote: String)
                        (endQuote: String = startQuote) =
    if (text.startsWith(startQuote))
      text.substring(
        startQuote.length,
        text.length - (if (text.endsWith(endQuote)) endQuote.length else 0)
      )
    else
      null
}
