package org.jetbrains.plugins.scala.lang.parser.util

import com.intellij.lang.PsiBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.{IndentationRegion, ScalaPsiBuilder}
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockExpr
import org.jetbrains.plugins.scala.lang.parser.parsing.{Associativity, CommonUtils}
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, PsiBuilderExt, ScalaElementType}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

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

  /**
   * A symbolic identifier such as +, or approx_==, or an identifier in backticks
   *
   * @see https://dotty.epfl.ch/docs/reference/changed-features/operators.html#syntax-change-1
   * @see similar implementation in Scala 3 repo:<br>
   *      https://github.com/lampepfl/dotty/blob/590e15920eecf93a1a15ae328cb94e0be666ec2a/compiler/src/dotty/tools/dotc/parsing/Scanners.scala#L89<br>
   *      (dotty.tools.dotc.parsing.Scanners.TokenData#isOperator)
   */
  def isSymbolicIdentifier(s: String): Boolean = {
    val isBackquoted = s.head == '`' && s.last == '`'
    isBackquoted ||
      ScalaNamesValidator.isIdentifier(s) && ScalaNamesUtil.isOpCharacter(s.last)
  }

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
      case '*' | '/' | '%'           => 1
      case '+' | '-'                 => 2
      case ':'                       => 3
      case '<' | '>'                 => 4
      case '=' | '!'                 => 5
      case '&'                       => 6
      case '^'                       => 7
      case '|'                       => 8
      case Letter() | '$' | '_'      => 9
      case _                         => 0
    }
  }

  object Letter {
    def unapply(c: Char): Boolean = c.isLetter
  }

  def parseLoopUntilRBrace(braceReported: Boolean = false)(body: => Unit)(implicit builder: ScalaPsiBuilder): Unit = {
    var br = braceReported
    while (true) {
      body
      builder.getTokenTypeIgnoringOutdent match {
        case ScalaTokenTypes.tRBRACE =>
          builder.advanceLexer()
          return
        case ScalaTokenTypes.tLBRACE => //to avoid missing '{'
          if (!br) {
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
          if (!br) {
            builder error ErrMsg("rbrace.expected")
            br = true
          }
          builder.advanceLexer()
          if (builder.eof) {
            return
          }
      }
    }
  }

  def parseBalancedParenthesis(accepted: TokenSet, count: Int = 1)(implicit builder: ScalaPsiBuilder): Boolean = {
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
            case _ => return false
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
  def parseVarIdWithWildcardBinding(withComma: Boolean)(implicit builder: PsiBuilder): Boolean =
    builder.build(ScalaElementType.NAMING_PATTERN) {
      if (withComma) {
        builder.advanceLexer() // ,
      }

      if (builder.invalidVarId) {
        builder.advanceLexer() // id or _
        builder.advanceLexer() // @ or :
        eatShortSeqWildcardNext()
      } else {
        false
      }
    }

  // _* (Scala 2 version of wildcard)
  def eatShortSeqWildcardNext()(implicit builder: PsiBuilder): Boolean = {
    val marker = builder.mark()
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
  def parseRuleInBlockOrIndentationRegion(region: IndentationRegion, @Nls expectedMessage: String)(rule: => Boolean)(implicit builder: ScalaPsiBuilder): Unit = {
    CommonUtils.eatAllSemicolons()
    builder.getTokenType match {
      case null =>
        if (region.isBraced) {
          // ok when we are in indentation style
          builder.error(ErrMsg("rbrace.expected"))
        }
        return
      case ScalaTokenTypes.tRBRACE =>
        if (region.isBraced)
          builder.advanceLexer() // Ate }
        return
      case _ if builder.isOutdentHere =>
        return
      case _ if rule =>
        builder.getTokenType match {
          case ScalaTokenTypes.tRBRACE =>
            if (region.isBraced)
              builder.advanceLexer() // Ate }
            return
          case _ if builder.isOutdentHere =>
            return
          case ScalaTokenTypes.tSEMICOLON =>
          case _ if builder.newlineBeforeCurrentToken =>
          case _ =>
            builder.error(ErrMsg("semi.expected"))
            // don't advance, we already added error, let's continue parsing following expression if we can parse it
            //builder.advanceLexer()
        }
      case ScalaTokenTypes.tLBRACE =>
        // normal blocks are not allowed here,
        // but we have to parse them anyway
        // otherwise closing braces might be
        // matched to other, wrong opening braces
        builder.error(expectedMessage)
        BlockExpr()
      case _ =>
        builder.error(expectedMessage)
        builder.advanceLexer() // Ate something
    }
    parseRuleInBlockOrIndentationRegion(region, expectedMessage)(rule)
  }
}
