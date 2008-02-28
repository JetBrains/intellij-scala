package org.jetbrains.plugins.scala.lang.parser.parsing.patterns
/**
* @author Ilya Sergey
*/
import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._

object ArgTypePattern {
/*
ArgTypePattern ::=  varid
                    | _�
                    | Type
*/
  def parse(builder : PsiBuilder) : ScalaElementType = {
    builder.getTokenType match {
      case ScalaTokenTypes.tUNDER => {
        builder.advanceLexer
        ScalaElementTypes.ARG_TYPE_PATTERN
      }
      case ScalaTokenTypes.tIDENTIFIER if
        (builder.getTokenText.substring(1).toLowerCase ==
         builder.getTokenText.substring(1)) => {
        builder.advanceLexer
        ScalaElementTypes.ARG_TYPE_PATTERN
      }
      case _ => {
        val res = Type.parse(builder)
        if (!res)
          ScalaElementTypes.ARG_TYPE_PATTERN
        else ScalaElementTypes.WRONGWAY
      }
    }
  }
}

object ArgTypePatterns {
/*
    ArgTypePattern {, ArgTypePattern}
*/
  def parse(builder : PsiBuilder) : ScalaElementType = {
    var res = ArgTypePattern.parse(builder)

    def subParse : ScalaElementType = {
      builder.getTokenType match {
        case ScalaTokenTypes.tCOMMA => {
          val rb = builder.mark()
          builder.advanceLexer
          res = ArgTypePattern.parse(builder)
          if (ScalaElementTypes.ARG_TYPE_PATTERN.equals(res)) {
            rb.drop()
            subParse
          }
          else {
            rb.rollbackTo()
            ScalaElementTypes.ARG_TYPE_PATTERNS
          }
        }
        case _ => {
          ScalaElementTypes.ARG_TYPE_PATTERNS
        }
      }
    }
    if (ScalaElementTypes.ARG_TYPE_PATTERN.equals(res)) subParse
    else {
      builder.error("Wrong argument type patterns")
      res
    }
  }
}

object TypePatternArgs {
/*
    TypePatternArgs ::= [ ArgTypePatterns ]
*/
  def parse(builder : PsiBuilder) : ScalaElementType = {
    if (ScalaTokenTypes.tLSQBRACKET.equals(builder.getTokenType)) {
      val tpaMarker = builder.mark()
      def end(msg: String) = {
        builder.error(msg)
        tpaMarker.done(ScalaElementTypes.TYPE_PATTERN_ARGS)
        ScalaElementTypes.TYPE_PATTERN_ARGS
      }

      builder.advanceLexer
      if (!ScalaElementTypes.WRONGWAY.equals(ArgTypePatterns.parse(builder))) {
        if (ScalaTokenTypes.tRSQBRACKET.equals(builder.getTokenType)) {
          builder.advanceLexer
        } else builder.error ("] expected")
      } else builder.error("Argument type patterns expected")

      tpaMarker.done(ScalaElementTypes.TYPE_PATTERN_ARGS)
      ScalaElementTypes.TYPE_PATTERN_ARGS
    }
    else ScalaElementTypes.WRONGWAY
  }
}

object SimpleTypePattern1 {
/*
    SimpleTypePattern1 ::=  SimpleTypePattern1 "#" Id
                            | StableId
                            | Path . type
*/
  def parse(builder : PsiBuilder) : ScalaElementType = {

    var spm = builder.mark()
    var res = StableId.parse(builder)

    def leftRecursion(currentMarker: PsiBuilder.Marker): ScalaElementType = {
      if (ScalaTokenTypes.tINNER_CLASS.equals(builder.getTokenType)) {
        builder.advanceLexer
        if (ScalaTokenTypes.tIDENTIFIER.equals(builder.getTokenType)) {
          builder.advanceLexer
          val next = currentMarker.precede()
          currentMarker.done(ScalaElementTypes.SIMPLE_TYPE_PATTERN1)
          leftRecursion(next)
        } else {
          builder.error("Identifier expected")
          currentMarker.done(ScalaElementTypes.SIMPLE_TYPE_PATTERN1)
          ScalaElementTypes.SIMPLE_TYPE_PATTERN1
        }
      } else {
        currentMarker.drop()
        ScalaElementTypes.SIMPLE_TYPE_PATTERN1
      }
    }

    def dotType = {
      if (ScalaTokenTypes.tDOT.equals(builder.getTokenType)) {
        builder.advanceLexer
        if (ScalaTokenTypes.kTYPE.equals(builder.getTokenType)) {
          builder.advanceLexer
          val next = spm.precede()
          spm.done(ScalaElementTypes.SIMPLE_TYPE_PATTERN1)
          leftRecursion(next)
        } else {
          builder.error("Keyword type expected")
          spm.done(ScalaElementTypes.SIMPLE_TYPE_PATTERN1)
          ScalaElementTypes.SIMPLE_TYPE_PATTERN1
        }
      } else {
        builder.error("Dot expected")
        spm.done(ScalaElementTypes.SIMPLE_TYPE_PATTERN1)
        ScalaElementTypes.SIMPLE_TYPE_PATTERN1
      }
    }
    if (ScalaElementTypes.PATH.equals(res)) {
      dotType
    } else {
      if (ScalaTokenTypes.tDOT.equals(builder.getTokenType)){
        dotType
      } else {
        if (ScalaTokenTypes.tINNER_CLASS.equals(builder.getTokenType)) {
          val next = spm.precede()
          spm.done(ScalaElementTypes.SIMPLE_TYPE_PATTERN1)
          leftRecursion(next)
        } else {
          spm.drop()
          ScalaElementTypes.SIMPLE_TYPE_PATTERN1
        }
      }
    }
  }
}

object SimpleTypePattern {
/*
    SimpleTypePattern ::= SimpleTypePattern1 [ TypePatternArgs ]
                          | { [ArgTypePattern , [ArgTypePatterns [,]]] }
*/

  def parse(builder : PsiBuilder) : ScalaElementType = {

    val spm = builder.mark()

    def closeParent = {
      if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)){
        builder.advanceLexer
        spm.done(ScalaElementTypes.SIMPLE_TYPE_PATTERN)
        ScalaElementTypes.SIMPLE_TYPE_PATTERN
      }
      else {
        spm.error("} expected")
        ScalaElementTypes.SIMPLE_TYPE_PATTERN
      }
    }

    builder.getTokenType match {
      case ScalaTokenTypes.tLBRACE => {
        val uMarker = builder.mark()
        builder.advanceLexer
        if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)){
          builder.advanceLexer
          uMarker.done(ScalaElementTypes.UNIT)
          ScalaElementTypes.SIMPLE_TYPE_PATTERN
        } else {
          uMarker.drop()
          var res = ArgTypePattern.parse(builder)
          if (!ScalaElementTypes.WRONGWAY.equals(res)) {
            if (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)){
              builder.advanceLexer
              if (ScalaTokenTypes.tRBRACE.equals(builder.getTokenType)){
                closeParent
              } else {
                val res1 = ArgTypePatterns.parse(builder)
                if (!ScalaElementTypes.WRONGWAY.equals(res1)) {
                  if (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)) builder.advanceLexer
                  closeParent
                } else closeParent
              }
            } else {
              builder.error(", expected")
              spm.done(ScalaElementTypes.SIMPLE_TYPE_PATTERN)
              ScalaElementTypes.SIMPLE_TYPE_PATTERN
            }
          } else {
            spm.error("Argument type pattern expected")
            ScalaElementTypes.SIMPLE_TYPE_PATTERN
          }
        }
      }
      case _ => {
        val res = SimpleTypePattern1.parse(builder)
        if (ScalaElementTypes.SIMPLE_TYPE_PATTERN1.equals(res)){
          if (ScalaTokenTypes.tLSQBRACKET.equals(builder.getTokenType)){
            TypePatternArgs.parse(builder)
            spm.done(ScalaElementTypes.SIMPLE_TYPE_PATTERN)
            ScalaElementTypes.SIMPLE_TYPE_PATTERN
          } else {
            spm.drop()
            ScalaElementTypes.SIMPLE_TYPE_PATTERN
          }
        } else {
          spm.drop()
          ScalaElementTypes.SIMPLE_TYPE_PATTERN
        }
      }
    }
  }
}

object TypePattern {

/*
    TypePattern ::= SimpleTypePattern {with SimpleTypePattern}
*/

  def parse(builder : PsiBuilder) : ScalaElementType = {

    val tpm = builder.mark()

    def subParse: ScalaElementType = {
      if (ScalaTokenTypes.kWITH.equals(builder.getTokenType)) {
        builder.advanceLexer
        SimpleTypePattern.parse(builder)
        subParse
      } else {
        tpm.done(ScalaElementTypes.TYPE_PATTERN)
        ScalaElementTypes.TYPE_PATTERN
      }
    }

    SimpleTypePattern.parse(builder)
    subParse
  }

}


