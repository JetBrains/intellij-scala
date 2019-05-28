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
import com.intellij.psi.util.PsiLiteralUtil
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

  override def typeForNullWithoutImplicits_=(`type`: Option[ScType]): Unit = literalElementType match {
    case T.kNULL => typeForNullWithoutImplicits_ = `type`
    case elementType => throw new AssertionError(s"Only null literals accepted, type: $elementType")
  }

  override def typeForNullWithoutImplicits: Option[ScType] = typeForNullWithoutImplicits_

  def isValidHost: Boolean = getValue.isInstanceOf[String]

  override def toString: String = "Literal"

  protected override def innerType: result.TypeResult =
    ScLiteralType.inferType(this)

  @CachedInUserData(this, util.PsiModificationTracker.MODIFICATION_COUNT)
  def getValue: AnyRef = {
    val node = literalNode

    node.getElementType match {
      case T.kTRUE => jl.Boolean.TRUE
      case T.kFALSE => jl.Boolean.FALSE
      case T.tSTRING |
           T.tWRONG_STRING =>
        trimQuotes(SingleLineQuote)() match {
          case null => null
          case stringText =>
            try {
              StringContext.treatEscapes(stringText) // for octal escape sequences
            } catch {
              case _: StringContext.InvalidEscapeException => StringUtil.unescapeStringCharacters(getText)
            }
        }
      case T.tMULTILINE_STRING =>
        trimQuotes(MultiLineQuote)()
      case T.tCHAR =>
        trimQuotes(CharQuote)() match {
          case null => null
          case chars =>
            val outChars = new jl.StringBuilder
            val success = java.PsiLiteralExpressionImpl.parseStringCharacters(
              chars,
              outChars,
              null
            )

            if (success && outChars.length == 1) Character.valueOf(outChars.charAt(0))
            else null
        }
      case T.tSYMBOL =>
        trimQuotes(CharQuote)("") match {
          case null => null
          case symbolText => Symbol(symbolText)
        }
      case T.tIDENTIFIER if node.getText == "-" =>
        nodeNumberValue(node.getTreeNext.getElementType)
      case elementType => nodeNumberValue(elementType)
    }
  }

  def updateText(text: String): ScLiteral = {
    literalNode match {
      case leaf: LeafElement => leaf.replaceWithText(text)
    }
    this
  }

  def createLiteralTextEscaper: LiteralTextEscaper[ScLiteralImpl] =
    if (isMultiLineString) new PassthroughLiteralEscaper(this)
    else new ScLiteralEscaper(this)

  private def literalNode = getNode.getFirstChildNode

  private def literalElementType = literalNode.getElementType

  override def isString: Boolean = literalElementType match {
    case T.tMULTILINE_STRING | T.tSTRING => true
    case _ => false
  }

  override def isMultiLineString: Boolean = literalElementType == T.tMULTILINE_STRING

  override def isSymbol: Boolean = literalElementType == T.tSYMBOL

  override def isChar: Boolean = literalElementType == T.tCHAR

  override def getReferences: Array[PsiReference] = PsiReferenceService.getService.getContributedReferences(this)

  override def contentRange: TextRange = {
    val maybeShifts = literalElementType match {
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

  private def nodeNumberValue(elementType: IElementType): Number = {
    def parseNumber(suffix: Char)
                   (function1: String => Number)
                   (function2: String => Number) = getText match {
      case text if endsWithIgnoreCase(text, suffix) => function1(text)
      case text => function2(text)
    }

    import PsiLiteralUtil._
    elementType match {
      case T.tFLOAT => parseNumber('f')(parseFloat)(parseDouble)
      case T.tINTEGER => parseNumber('l')(parseLong)(parseInteger)
      case _ => null
    }
  }

  private def trimQuotes(startQuote: String)
                        (endQuote: String = startQuote) = getText match {
    case text if text.startsWith(startQuote) =>
      val beginIndex = startQuote.length
      text.length - (if (text.endsWith(endQuote)) endQuote.length else 0) match {
        case endIndex if endIndex < beginIndex => null
        case endIndex => text.substring(beginIndex, endIndex)
      }
    case _ => null
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

  private def endsWithIgnoreCase(text: String,
                                 suffix: Char) =
    StringUtil.endsWithChar(text, suffix) ||
      StringUtil.endsWithChar(text, suffix.toUpper)
}
