package org.jetbrains.plugins.scala
package lang
package parser
package util

import com.intellij.lang.PsiBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.Associativity
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockExpr
import org.jetbrains.plugins.scala.project.{ProjectPsiElementExt, ScalaLanguageLevel}

object ParserUtils {

  def compareOperators(op1: String, op2: String, assignements: Boolean = false)(implicit builder: ScalaPsiBuilder): Boolean = {
    val isScala213OrNewer = builder.scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_2_13)
    def opPrecedence(opText: String): Int = priority(opText, isScala213OrNewer, assignements)

    if (opPrecedence(op1) < opPrecedence(op2)) true //  a * b + c  =((a * b) + c)
    else if (opPrecedence(op1) > opPrecedence(op2)) false //  a + b * c = (a + (b * c))
    else if (operatorAssociativity(op1) == operatorAssociativity(op2))
      if (operatorAssociativity(op1) == Associativity.Right) true
      else false
    else {
      builder error ErrMsg("wrong.type.associativity")
      false
    }
  }

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
  def priority(id: String, is213orNewer: Boolean, assignments: Boolean): Int = {
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
      case Letter()                  => 9
      case '$' | '_' if is213orNewer => 9
      case _                         => 0
    }
  }

  def priority(op: PsiElement, assignments: Boolean = false): Int =
    priority(op.getText, op.scalaLanguageLevelOrDefault >= ScalaLanguageLevel.Scala_2_13, assignments)

  object Letter {
    def unapply(c: Char): Boolean = c.isLetter
  }

  def parseLoopUntilRBrace(braceReported: Boolean = false)(body: => Unit)(implicit builder: ScalaPsiBuilder): Unit = {
    var br = braceReported
    while (true) {
      body
      builder.getTokenType match {
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
  def parseRuleInBlockOrIndentationRegion(
    blockIndentation: BlockIndentation,
    baseIndentation: Option[IndentationWidth],
    @Nls expectedMessage: String
  )(rule: => Boolean)(implicit builder: ScalaPsiBuilder): Unit = {
    skipSemicolon(blockIndentation)
    blockIndentation.fromHere()
    builder.getTokenType match {
      case null =>
        if (baseIndentation.isEmpty) {
          // ok when we are in indentation style
          builder.error(ErrMsg("rbrace.expected"))
        }
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
