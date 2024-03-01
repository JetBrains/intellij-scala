package org.jetbrains.plugins.scala.lang.parser.parsing.builder

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.IndentationWidth

import scala.annotation.tailrec

/**
 * The IndentationRegion is the new way of handling indentation based syntax in Scala 3.
 * Instead of comparing the indentations to some current indentation on top of the indentation stack,
 * we replace the indentation stack with a stack of IndentationRegions and use the isIndent/isOutdent
 * methods.
 *
 * An indentation region is a more abstract and precise way to interact with the currently applicable
 * indentation rules. Notably, it can handle braced blocks, single expressions, and enables more accurate
 * handling of end markers.
 *
 * Note that the base class does not expose the actual indentation with, even though all subclasses do have a
 * certain indentation width. This is done so the client code in the parser doesn't try to use it,
 * which may easily lead to incorrect behaviour, because edge cases of parsing indentations within certain regions
 * are easily disregarded.
 */
sealed abstract class IndentationRegion {
  def isBraced: Boolean

  /**
   * Checks if the 'indent' is a valid indentation for an end marker in the current region.
   */
  def isValidEndMarkerIndentation(indent: IndentationWidth): Boolean

  def isOutdent(indent: IndentationWidth): Boolean
  def isIndent(indent: IndentationWidth): Boolean

  final def isOutdent(indent: Option[IndentationWidth]): Boolean = indent.exists(isOutdent)
  final def isIndent(indent: Option[IndentationWidth]): Boolean = indent.exists(isIndent)

  def isOutdentForLeadingInfixOperator(indent: IndentationWidth)(implicit builder: ScalaPsiBuilder): Boolean
}

object IndentationRegion {
  val initial: IndentationRegion = Indented(IndentationWidth.initial)(None)

  /**
   * A region where only a single expression is parsed.
   * This is important, because constructs that appear in this region cannot have an end marker.
   *
   * Example:
   *      def hahahaha = { for(x <- 0 to 10) (); if true then while(false) 0
   *        end if   // 'if' can have the end marker here, but 'while' cannot
   *      }
   */
  final case class SingleExpr(outerRegion: IndentationRegion) extends IndentationRegion {
    override def isBraced: Boolean = false
    override def isValidEndMarkerIndentation(indent: IndentationWidth): Boolean = false

    val outerNonSingleExprRegion: IndentationRegion = outerRegion match {
      case region: SingleExpr => region.outerNonSingleExprRegion
      case _             => outerRegion
    }

    override def isOutdent(indent: IndentationWidth): Boolean = outerNonSingleExprRegion.isOutdent(indent)
    override def isIndent(indent: IndentationWidth): Boolean = outerNonSingleExprRegion.isIndent(indent)

    override def isOutdentForLeadingInfixOperator(indent: IndentationWidth)(implicit builder: ScalaPsiBuilder): Boolean =
      outerNonSingleExprRegion.isOutdentForLeadingInfixOperator(indent)
  }

  /**
   * From the reference:
   *
   * The indentation rules for match expressions and catch clauses are refined as follows:
   *
   * - An indentation region is opened after a match or catch also if the following case appears
   *   at the indentation width that's current for the match itself.
   * - In that case, the indentation region closes at the first token at that same indentation
   *   width that is not a case, or at any token with a smaller indentation width, whichever comes first.
   */
  final case class BracelessCaseClause(inner: Indented) extends IndentationRegion {
    override def isBraced: Boolean = false
    override def isValidEndMarkerIndentation(indent: IndentationWidth): Boolean = inner.isValidEndMarkerIndentation(indent)

    override def isOutdent(indent: IndentationWidth): Boolean = indent <= inner.indentation // here is the difference
    override def isIndent(indent: IndentationWidth): Boolean = inner.isIndent(indent)

    override def isOutdentForLeadingInfixOperator(indent: IndentationWidth)(implicit builder: ScalaPsiBuilder): Boolean =
      inner.isOutdentForLeadingInfixOperator(indent)
  }

  /**
   * Normally indented region
   */
  final case class Indented(indentation: IndentationWidth)(val outerRegion: Option[IndentationRegion]) extends IndentationRegion {
    override def isBraced: Boolean = false
    override def isValidEndMarkerIndentation(indent: IndentationWidth): Boolean = indent >= indentation

    override def isOutdent(indent: IndentationWidth): Boolean = indent < indentation
    override def isIndent(indent: IndentationWidth): Boolean = indent > indentation

    override def isOutdentForLeadingInfixOperator(indent: IndentationWidth)(implicit builder: ScalaPsiBuilder): Boolean = {
      def isOnPreviousIndent(region: IndentationRegion): Boolean = builder.allPreviousIndentations(region).contains(indent)

      indent < indentation && {
        @tailrec
        def outdents(region: IndentationRegion): Boolean = region match {
          case SingleExpr(outer) => outdents(outer)
          case Indented(outerIndent) => indent <= outerIndent || isOnPreviousIndent(region)
          case BracelessCaseClause(outerIndent) => indent <= outerIndent.indentation || isOnPreviousIndent(region)
          case _: Braced => false
        }

        outerRegion.exists(outdents)
      }
    }
  }

  /**
   * The region within two braces
   */
  sealed abstract class Braced extends IndentationRegion {
    override def isBraced: Boolean = true
    override def isValidEndMarkerIndentation(indent: IndentationWidth): Boolean = true

    // no matter the indention, it you cannot outdent a braced block
    override final def isOutdent(indent: IndentationWidth): Boolean = false
    override final def isOutdentForLeadingInfixOperator(indent: IndentationWidth)(implicit builder: ScalaPsiBuilder): Boolean = false
  }

  object Braced {
    def fromHere(implicit builder: ScalaPsiBuilder): Braced = {
      builder.findPrecedingIndentation match {
        case Some(indent) => Concrete(indent)
        case None         => new Lazy(builder.rawTokenIndex, builder)
      }
    }

    /**
     * Braced region where an indentation follows directly after the '{'
     */
    final case class Concrete(indentation: IndentationWidth) extends Braced {
      override def isIndent(indent: IndentationWidth): Boolean = indent > indentation
    }

    /**
     * This version of the [[IndentationRegion.Braced]] is used in the case when a block is opened
     * and directly followed with tokens without a newline in between.
     * i.e.
     *
     *   def test = { println(1)
     *     println(2)   // indentation width of this braced block is 2 spaces
     *   }
     *
     * The indentation width of that block is supposed to be the first indentation width that occurs
     * within the block. From the specs (https://docs.scala-lang.org/scala3/reference/other-new-features/indentation.html#indentation-and-braces-1)
     *
     * 2.1) The assumed indentation width of a multiline region enclosed in braces is the indentation width
     *      of the first token that starts a new line after the opening brace.
     *
     * The problem is, that when we create the indentation region we don't yet know the indentation width
     * in the case mentioned above. Otherwise we would have used [[IndentationRegion.Braced.Concrete]].
     *
     * Also in most case we don't actually need to know the indentation width of the block,
     * so we are using a very lazy approach, where we only search for the indentation width
     * when it's really needed and only so far as it's needed to answer the current request.
     *
     * @param regionStartRawTokenIndex is the raw token index where the block starts.
     *                                 From here we need to start looking for the indentation.
     */
    final class Lazy(private var regionStartRawTokenIndex: Int, builder: ScalaPsiBuilder) extends Braced {

      private var foundIndentation: Option[IndentationWidth] = None

      override def isIndent(indent: IndentationWidth): Boolean = {
        findIndentationUntilHere() match {
          case Some(indentation) => indent > indentation
          case None              =>
            // we haven't found any indentation up until the current token
            // that means the `indent` argument cannot be from within this block region
            throw new AssertionError("Trying to check the indentation of block region with an indentation that is not from within this block region")
        }
      }

      private def findIndentationUntilHere(): Option[IndentationWidth] = {
        foundIndentation match {
          case Some(indentation) => return Some(indentation)
          case None              =>
        }

        // continue searching for an indentation
        @tailrec
        def findForward(steps: Int): Option[IndentationWidth] = {
          if (steps > 0) return None

          if (!ScalaTokenTypes.WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(builder.rawLookup(steps))) {
            val result = lookBehindForPrecedingIndentation(builder, steps)
            if (result.isDefined) {
              return result
            }
          }

          findForward(steps + 1)
        }

        findForward(regionStartRawTokenIndex - builder.rawTokenIndex) match {
          case Some(indentation) =>
            foundIndentation = Some(indentation)
            Some(indentation)
          case None =>
            // we haven't found any indentation up until the current token
            // next time we will start searching from the current token
            regionStartRawTokenIndex = builder.rawTokenIndex
            None
        }
      }
    }
  }
}