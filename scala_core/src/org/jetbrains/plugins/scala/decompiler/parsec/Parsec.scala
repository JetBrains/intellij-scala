package org.jetbrains.plugins.scala.decompiler.parsec
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType

/**
 * @author veilyas
 */

abstract class Parser {

  import ParserConversions._

  //main method to override
  def do_parse(builder: PsiBuilder): Boolean

  def parse = do_parse _

  // Combine two sequent parsers
  def >(p: Parser) = {
    def parseInner = parse
    new Parser {
      def do_parse(builder: PsiBuilder) = {
        val marker = builder.mark()
        if (parseInner(builder) && p.parse(builder)) {
          marker.drop
          true
        } else {
          marker.rollbackTo
          false
        }
      }
    }
  }

  // Kleene's closure
  def * = {
    def parseInner = parse
    new Parser {
      def do_parse(builder: PsiBuilder) = {
        while (parseInner(builder)) ()
        true
      }
    }
  }

  // Optional production
  def ? = {
    def parseInner = parse
    new Parser {
      def do_parse(builder: PsiBuilder) = {
        parseInner(builder)
        true
      }
    }
  }

  def |(p: Parser) = {
    def parseInner = parse
    new Parser {
      def do_parse(builder: PsiBuilder) = {
        parseInner(builder) ||
            p.parse(builder)
      }
    }
  }

  def or = | _

  def /(message: String) = {
    def parseInner = parse
    new Parser {
      def do_parse(builder: PsiBuilder) = {
        if (!parseInner(builder)) builder.error(message)
        true
      }
    }
  }

  // Create AST node
  def |>(t: IElementType) = {
    def parseInner = parse
    new Parser {
      def do_parse(builder: PsiBuilder) = {
        val marker = builder.mark()
        val res = parseInner(builder)
        if (res) marker.done(t) else marker.drop()
        res
      }
    }
  }

}

/**
 * Class to define new parser grammar
 */
class Memo {
  var v = false

  implicit object P extends Parser {
    def do_parse(builder: PsiBuilder) = v
  }

  //Define new Grammar Production
  def __(p: Parser) = new Parser {
    def do_parse(builder: PsiBuilder) = {
      v = p.parse(builder)
      v
    }
  }
}


/*
 Necessary conversion
 */
object ParserConversions {
  implicit def token2parser(t: IElementType) = new Parser {
    def do_parse(builder: PsiBuilder) = {
      builder.getTokenType() match {
        case `t` => {
          builder.advanceLexer()
          true
        }
        case _ => false
      }
    }
  }

  def _define = new Memo

  val PLAIN_PARSER = new Parser {
    override def do_parse(builder: PsiBuilder) = {
      while (!builder.eof) builder.advanceLexer
      true
    }
  }
}
