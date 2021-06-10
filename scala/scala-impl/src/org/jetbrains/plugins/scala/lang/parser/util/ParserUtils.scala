package org.jetbrains.plugins.scala
package lang
package parser
package util

import com.intellij.lang.PsiBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.Associativity
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

import scala.annotation.tailrec

object ParserUtils {
  //Associations of operator
  def operatorAssociativity(id: String): Associativity.LeftOrRight = {
    id.last match {
      case ':' => Associativity.Right
      case _ => Associativity.Left
    }
  }

  def isAssignmentOperator: String => Boolean = {
    case "==" | "!=" | "<=" | ">=" => false
    case "=" => true
    case id => id.head != '=' && id.last == '='
  }

  // a symbolic identifier such as +, or approx_==, or an identifier in backticks
  def isSymbolicIdentifier(s: String): Boolean =
    !s.forall(_.isUnicodeIdentifierPart)

  /** Defines the precedence of an infix operator, according
    * to its first character.
    *
    * @param id          The identifier
    * @param assignments Consider assignment operators have lower priority than other non-special characters
    * @return An integer value. Lower value means higher precedence
    */
  def priority(id: String, assignments: Boolean = false): Int = {
    if (assignments && isAssignmentOperator(id)) {
      return 10
    }
    id.charAt(0) match {
      case '~' | '#' | '@' | '?' | '\\' => 0 //todo: other special characters?
      case '*' | '/' | '%' => 1
      case '+' | '-' => 2
      case ':' => 3
      case '<' | '>' => 4
      case '=' | '!' => 5
      case '&' => 6
      case '^' => 7
      case '|' => 8
      case _ => 9
    }
  }

  @tailrec
  def parseLoopUntilRBrace(builder: ScalaPsiBuilder, fun: () => Unit, braceReported: Boolean = false): Unit = {
    var br = braceReported
    fun()
    builder.getTokenType match {
      case ScalaTokenTypes.tRBRACE =>
        builder.advanceLexer()
        return
      case ScalaTokenTypes.tLBRACE => //to avoid missing '{'
        if (!braceReported) {
          builder error ErrMsg("rbrace.expected")
          br = true
        }
        var balance = 1
        builder.advanceLexer()
        while (balance != 0 && !builder.eof) {
          builder.getTokenType match {
            case ScalaTokenTypes.tRBRACE => balance -= 1
            case ScalaTokenTypes.tLBRACE => balance += 1
            case _ =>
          }
          builder.advanceLexer()
        }
        if (builder.eof)
          return
      case _ =>
        if (!braceReported) {
          builder error ErrMsg("rbrace.expected")
          br = true
        }
        builder.advanceLexer()
        if (builder.eof) {
          return
        }
    }
    parseLoopUntilRBrace(builder, fun, br)
  }

  def parseBalancedParenthesis(builder: ScalaPsiBuilder, accepted: TokenSet, count: Int = 1): Boolean = {
    var seen = 0

    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS =>
        var count = 1
        builder.advanceLexer()

        while (count > 0 && !builder.eof()) {
          builder.getTokenType match {
            case ScalaTokenTypes.tLPARENTHESIS => count += 1
            case ScalaTokenTypes.tRPARENTHESIS => count -= 1
            case acc if accepted.contains(acc) => seen += 1
            case o => return false
          }

          builder.advanceLexer()
        }
      case _ =>
    }

    seen == count
  }

  def hasTextBefore(builder: ScalaPsiBuilder, text: String): Boolean = {
    Option(builder.getLatestDoneMarker).exists {
      marker =>
        StringUtil.equals(builder.getOriginalText.subSequence(marker.getStartOffset, marker.getEndOffset - 1), text)
    }
  }

  // [,] id@_*
  def parseVarIdWithWildcardBinding(builder: PsiBuilder, withComma: Boolean): Boolean =
    builder.build(ScalaElementType.NAMING_PATTERN) { repr =>
      if (withComma) {
        repr.advanceLexer() // ,
      }

      if (repr.invalidVarId) {
        repr.advanceLexer() // id or _
        repr.advanceLexer() // @ or :
        eatShortSeqWildcardNext(repr)
      } else {
        false
      }
    }

  // _* (Scala 2 version of wildcard)
  def eatShortSeqWildcardNext(builder: PsiBuilder): Boolean = {
    val marker = builder.mark
    if (builder.getTokenType == ScalaTokenTypes.tUNDER) {
      builder.advanceLexer()
      if (builder.getTokenType == ScalaTokenTypes.tIDENTIFIER && builder.getTokenText == "*") {
        builder.advanceLexer()
        marker.done(ScalaElementType.SEQ_WILDCARD_PATTERN)
        true
      } else {
        marker.rollbackTo()
        false
      }
    } else {
      marker.drop()
      false
    }
  }


  /*
   * [[Stat]] { semi [[Stat]] }
   */
  @annotation.tailrec
  def parseRuleInBlockOrIndentationRegion(
    blockIndentation: BlockIndentation,
    baseIndentation: Option[IndentationWidth],
    @Nls expectedMessage: String
  )(rule: => Boolean)(implicit builder: ScalaPsiBuilder): Unit = {
    skipSemicolon(blockIndentation)
    blockIndentation.fromHere()
    builder.getTokenType match {
      case null =>
        builder.error(ErrMsg("rbrace.expected"))
        return
      case ScalaTokenTypes.tRBRACE =>
        if (baseIndentation.isEmpty)
          builder.advanceLexer() // Ate }
        return
      case _ if isOutdent(baseIndentation) =>
        return
      case _ if rule =>
        builder.getTokenType match {
          case ScalaTokenTypes.tRBRACE =>
            if (baseIndentation.isEmpty)
              builder.advanceLexer() // Ate }
            return
          case _ if isOutdent(baseIndentation) =>
            return
          case ScalaTokenTypes.tSEMICOLON =>
          case _ if builder.newlineBeforeCurrentToken =>
          case _ =>
            builder.error(ErrMsg("semi.expected"))
            // don't advance, we already added error, let's continue parsing following expression if we can parse it
            //builder.advanceLexer()
        }
      case _ =>
        builder.error(expectedMessage)
        builder.advanceLexer() // Ate something
    }
    parseRuleInBlockOrIndentationRegion(blockIndentation, baseIndentation, expectedMessage)(rule)
  }

  def isOutdent(baseIndentation: Option[IndentationWidth])(implicit builder: ScalaPsiBuilder): Boolean =
    baseIndentation.exists(cur => builder.findPreviousIndent.exists(_ < cur) || builder.eof())

  private def skipSemicolon(blockIndentation: BlockIndentation)(implicit builder: ScalaPsiBuilder): Unit =
    while (builder.getTokenType == ScalaTokenTypes.tSEMICOLON) {
      blockIndentation.fromHere()
      builder.advanceLexer()
    }
}
