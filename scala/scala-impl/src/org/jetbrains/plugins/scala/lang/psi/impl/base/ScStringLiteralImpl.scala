package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.tree.IElementType
import org.apache.commons.lang.StringEscapeUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.QuotedLiteralImplBase
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.escapers.{ScLiteralEscaper, ScLiteralRawEscaper}
import org.jetbrains.plugins.scala.lang.psi.types._

// todo: move to "literals" subpackage, but check usages

class ScStringLiteralImpl(node: ASTNode,
                          override val toString: String)
  extends QuotedLiteralImplBase(node, toString)
    with ScStringLiteral {

  import QuotedLiteralImplBase._
  import ScStringLiteralImpl._
  import lang.lexer.ScalaTokenTypes._

  override protected def startQuote: String =
    if (isMultiLineString) MultiLineQuote
    else if (isString) SingleLineQuote
    else ""

  override protected final def toValue(text: String): String = literalElementType match {
    case `tSTRING` =>
      try {
        StringContext.processEscapes(text) // for octal escape sequences
      } catch {
        case _: StringContext.InvalidEscapeException =>
          StringUtil.unescapeStringCharacters(getText)
        case e: IllegalArgumentException if e.getMessage.contains("invalid unicode escape") =>
          StringUtil.unescapeStringCharacters(getText)
      }
    case _ => text
  }

  override protected final def wrappedValue(value: String): ScLiteral.Value[String] =
    Value(value)

  override def isString: Boolean = literalElementType != `tWRONG_STRING`

  override def isMultiLineString: Boolean = literalElementType == `tMULTILINE_STRING`

  override def isValidHost: Boolean = getValue != null

  override def updateText(text: String): ScStringLiteralImpl = {
    firstNode match {
      case leaf: LeafElement => leaf.replaceWithText(text)
    }
    this
  }

  override def createLiteralTextEscaper: LiteralTextEscaper[ScStringLiteral] =
    if (isMultiLineString) new ScLiteralRawEscaper(this)
    else new ScLiteralEscaper(this)

  override def getReferences: Array[PsiReference] = PsiReferenceService.getService.getContributedReferences(this)

  protected final def firstNode: ASTNode = getNode.getFirstChildNode

  private def literalElementType: IElementType = firstNode.getElementType
}

object ScStringLiteralImpl {

  final case class Value(override val value: String) extends ScLiteral.Value(value) {

    import QuotedLiteralImplBase.SingleLineQuote

    override def presentation: String = SingleLineQuote + StringEscapeUtils.escapeJava(super.presentation) + SingleLineQuote

    override def wideType(implicit project: Project): ScType = cachedClass(CommonClassNames.JAVA_LANG_STRING)
  }
}
