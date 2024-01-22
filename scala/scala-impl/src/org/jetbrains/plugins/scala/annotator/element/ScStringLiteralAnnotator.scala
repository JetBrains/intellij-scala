package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.ASTNode
import com.intellij.lexer.StringLiteralLexer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.StringEscapesTokenTypes
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.highlighter.lexer.{ScalaInterpolatedStringLiteralLexer, ScalaMultilineStringLiteralLexer, ScalaStringLiteralLexer}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{tINTERPOLATED_MULTILINE_STRING, tINTERPOLATED_STRING, tMULTILINE_STRING, tSTRING}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

/** see also [[org.jetbrains.plugins.scala.annotator.element.ScInterpolatedStringLiteralAnnotator]] */
object ScStringLiteralAnnotator extends ElementAnnotator[ScStringLiteral] {

  private val StringLiteralSizeLimit = 65536
  private val StringCharactersCountLimit = StringLiteralSizeLimit / 4

  import scala.util.chaining.scalaUtilChainingOps

  private val SET_STRING                        = tSTRING.pipe(t => (t, TokenSet.create(t)))
  private val SET_MULTILINE_STRING              = tMULTILINE_STRING.pipe(t => (t, TokenSet.create(t)))
  private val SET_INTERPOLATED_STRING           = tINTERPOLATED_STRING.pipe(t => (t, TokenSet.create(t)))
  private val SET_INTERPOLATED_MULTILINE_STRING = tINTERPOLATED_MULTILINE_STRING.pipe(t => (t, TokenSet.create(t)))

  override def annotate(literal: ScStringLiteral, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (annotateTooLongString(literal))
      return

    annotateInvalidEscapeSequences(literal)
  }

  private def annotateInvalidEscapeSequences(literal: ScStringLiteral)
                                            (implicit holder: ScalaAnnotationHolder): Unit = {
    val isInterpolated = literal.is[ScInterpolatedStringLiteral]
    val isMultiline = literal.isMultiLineString

    val (tokenType, tokenSet) = (isInterpolated, isMultiline) match {
      case (false, false) => SET_STRING
      case (false, true)  => SET_MULTILINE_STRING
      case (true, false)  => SET_INTERPOLATED_STRING
      case (true, true)   => SET_INTERPOLATED_MULTILINE_STRING
    }

    val isRaw = literal.asOptionOf[ScInterpolatedStringLiteral].exists(_.kind == ScInterpolatedStringLiteral.Raw)
    val lexer: ScalaStringLiteralLexer = (isInterpolated, isMultiline) match {
      case (false, false) => new ScalaStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, tokenType)
      case (false, true)  => new ScalaMultilineStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, tokenType)
      case (true, _)  => new ScalaInterpolatedStringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, tokenType, isRaw, isMultiline)
    }

    val stringLeafNodes = literal.getNode.getChildren(tokenSet)
    stringLeafNodes.foreach(annotateInvalidEscapeSequences(_, lexer))
  }

  // NOTE: in platform, lexer is reused during highlighting, so we also reuse to catch potential issues
  private def annotateInvalidEscapeSequences(node: ASTNode, lexer: ScalaStringLiteralLexer)
                                            (implicit holder: ScalaAnnotationHolder): Unit = {
    lexer.start(node.getChars)

    var tokenType = lexer.getTokenType
    while (tokenType != null) {
      def range = TextRange.create(lexer.getTokenStart, lexer.getTokenEnd).shiftRight(node.getStartOffset)

      tokenType match {
        case StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN =>
          holder.createErrorAnnotation(range, ScalaBundle.message("string.literal.invalid.escape.character"))
        case StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN   =>
          holder.createErrorAnnotation(range, ScalaBundle.message("string.literal.invalid.unicode.escape"))
        case _                                                      =>
      }

      lexer.advance()
      tokenType = lexer.getTokenType
    }
  }

  private def annotateTooLongString(literal: ScStringLiteral)
                                   (implicit holder: ScalaAnnotationHolder): Boolean = {
    val isTooLong = literal match {
      case interpolated: ScInterpolatedStringLiteral => isTooLongLiteral(interpolated, interpolated.getStringParts: _*)
      case ScStringLiteral(string)                   => isTooLongLiteral(literal, string)
      case _                                         => false
    }
    if (isTooLong) {
      holder.createErrorAnnotation(literal, ScalaBundle.message("string.literal.is.too.long"))
    }
    isTooLong
  }

  private def isTooLongLiteral(literal: ScStringLiteral, strings: String*): Boolean = {
    implicit val virtualFile: Option[VirtualFile] = literal.containingVirtualFile
    strings.exists(exceedsLimit)
  }

  private def exceedsLimit(string: String)
                          (implicit virtualFile: Option[VirtualFile]): Boolean = string.length match {
    case length if length >= StringLiteralSizeLimit => true
    case length if length >= StringCharactersCountLimit => utf8Size(string) >= StringLiteralSizeLimit
    case _ => false
  }

  private def utf8Size(string: String)
                      (implicit virtualFile: Option[VirtualFile]): Int = {
    val lineSeparator = virtualFile
      .flatMap(virtualFile => Option(virtualFile.getDetectedLineSeparator))
      .getOrElse(Option(System.lineSeparator).getOrElse("\n"))

    string.map {
      case '\n' => lineSeparator.length
      case '\r' => 0
      case character if character >= 0 && character <= '\u007F' => 1
      case character if character >= '\u0080' && character <= '\u07FF' => 2
      case character if character >= '\u0800' && character <= '\uFFFF' => 3
      case _ => 4
    }.sum
  }
}
