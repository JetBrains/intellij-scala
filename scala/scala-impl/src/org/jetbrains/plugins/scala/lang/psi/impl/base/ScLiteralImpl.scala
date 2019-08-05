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
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.QuotedLiteralImplBase
import org.jetbrains.plugins.scala.lang.psi.types._

/**
 * @author Alexander Podkhalyuzin
 *         Date: 22.02.2008
 */
class ScLiteralImpl(node: ASTNode,
                    override val toString: String)
  extends QuotedLiteralImplBase(node, toString)
    with ContributedReferenceHost
    with PsiLanguageInjectionHost {

  import QuotedLiteralImplBase._
  import ScLiteralImpl._
  import lang.lexer.ScalaTokenTypes._

  override protected final type V = String

  override protected def startQuote: String =
    if (isMultiLineString) MultiLineQuote
    else if (isString) SingleLineQuote
    else ""

  override protected final def toValue(text: String): String = literalElementType match {
    case `tSTRING` =>
      try {
        StringContext.treatEscapes(text) // for octal escape sequences
      } catch {
        case _: StringContext.InvalidEscapeException => StringUtil.unescapeStringCharacters(getText)
      }
    case _ => text
  }

  override protected final def wrappedValue(value: String) =
    Value(value)

  override def isString: Boolean = literalElementType != `tWRONG_STRING`

  override def isMultiLineString: Boolean = literalElementType == `tMULTILINE_STRING`

  override def isValidHost: Boolean = getValue != null

  override def updateText(text: String): ScLiteralImpl = {
    firstNode match {
      case leaf: LeafElement => leaf.replaceWithText(text)
    }
    this
  }

  override def createLiteralTextEscaper: LiteralTextEscaper[ScLiteralImpl] =
    if (isMultiLineString) new PassthroughLiteralEscaper(this)
    else new ScLiteralEscaper(this)

  override def getReferences: Array[PsiReference] = PsiReferenceService.getService.getContributedReferences(this)

  protected final def firstNode: ASTNode = getNode.getFirstChildNode

  private def literalElementType: IElementType = firstNode.getElementType
}

object ScLiteralImpl {

  final case class Value(override val value: String) extends ScLiteral.Value(value) {

    import QuotedLiteralImplBase.SingleLineQuote

    override def presentation: String = SingleLineQuote + StringEscapeUtils.escapeJava(super.presentation) + SingleLineQuote

    override def wideType(implicit project: Project): ScType = cachedClass(CommonClassNames.JAVA_LANG_STRING)
  }
}
