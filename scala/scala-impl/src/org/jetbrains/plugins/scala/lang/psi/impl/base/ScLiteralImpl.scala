package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

/**
 * @author Alexander Podkhalyuzin
 *         Date: 22.02.2008
 */
class ScLiteralImpl(node: ASTNode,
                    override val toString: String) extends expr.ScExpressionImplBase(node)
  with ScLiteral with ContributedReferenceHost {

  import ScLiteralImpl._
  import lang.lexer.ScalaTokenTypes._

  def isValidHost: Boolean = getValue.isInstanceOf[String]

  protected override def innerType: result.TypeResult =
    ScLiteralType.inferType(this)

  @CachedInUserData(this, util.PsiModificationTracker.MODIFICATION_COUNT)
  def getValue: AnyRef = {
    import literals.QuotedLiteralImplBase._
    literalElementType match {
      case `tSTRING` | `tWRONG_STRING` =>
        trimQuotes(getText, SingleLineQuote)() match {
          case null => null
          case stringText =>
            try {
              StringContext.treatEscapes(stringText) // for octal escape sequences
            } catch {
              case _: StringContext.InvalidEscapeException => StringUtil.unescapeStringCharacters(getText)
            }
        }
      case `tMULTILINE_STRING` =>
        trimQuotes(getText, MultiLineQuote)()
      case _ => null
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

  protected final def literalNode: ASTNode = getNode.getFirstChildNode

  private def literalElementType = literalNode.getElementType

  override def isString: Boolean = literalElementType match {
    case `tMULTILINE_STRING` | `tSTRING` => true
    case _ => false
  }

  override def isMultiLineString: Boolean = literalElementType == `tMULTILINE_STRING`

  override def getReferences: Array[PsiReference] = PsiReferenceService.getService.getContributedReferences(this)

  override def contentRange: TextRange = {
    val maybeShifts = literalElementType match {
      case `tSTRING` => stringShifts(SingleLineQuote)
      case `tMULTILINE_STRING` => stringShifts(MultiLineQuote)
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
}

object ScLiteralImpl {

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
}
