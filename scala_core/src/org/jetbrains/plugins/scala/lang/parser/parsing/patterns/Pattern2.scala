package org.jetbrains.plugins.scala.lang.parser.parsing.patterns

import com.intellij.lang.PsiBuilder
import lexer.ScalaTokenTypes

/**
 * @author Alexander Podkhalyuzin
 *   Date: 29.02.2008
 */

/*
 * Pattern2 ::= varid '@' Pattern3
 *            | _ '@' Pattern3
 *            | Pattern3
 */

object Pattern2 {
  def parse(builder: PsiBuilder, forDef: Boolean): Boolean = {

    def isVarId = builder.getTokenText.substring(0, 1).toLowerCase != builder.getTokenText.substring(0, 1) || (
            builder.getTokenText.apply(0) == '`' && builder.getTokenText.apply(builder.getTokenText.length - 1) == '`'
            )

    def testForId = {
      val m = builder.mark
      builder.advanceLexer
      val s = Set(ScalaTokenTypes.tAT,
        ScalaTokenTypes.tIDENTIFIER,
        ScalaTokenTypes.tDOT,
        ScalaTokenTypes.tLPARENTHESIS)
      val b = !s.contains(builder.getTokenType)
      m.rollbackTo
      b
    }

    val pattern2Marker = builder.mark
    val backupMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.tIDENTIFIER => {
        if (forDef && testForId) {
          backupMarker.drop
          builder.advanceLexer
          pattern2Marker.done(ScalaElementTypes.REFERENCE_PATTERN)
          return true
        } else if (isVarId) {
          backupMarker.rollbackTo
        } else {
          builder.advanceLexer //Ate id
          val idMarker = builder.mark
          builder.getTokenType match {
            case ScalaTokenTypes.tAT => {
              builder.advanceLexer //Ate @
              backupMarker.drop
              if (!Pattern3.parse(builder)) {
                idMarker.rollbackTo
                pattern2Marker.done(ScalaElementTypes.REFERENCE_PATTERN)
                val err = builder.mark
                builder.advanceLexer
                err.error(ErrMsg("wrong.pattern"))
              } else {
                idMarker.drop
                pattern2Marker.done(ScalaElementTypes.NAMING_PATTERN)
              }
              return true
            }
            case _ => {
              idMarker.drop
              backupMarker.rollbackTo
            }
          }
        }
      }
      case ScalaTokenTypes.tUNDER => {
        builder.advanceLexer //Ate id
        val idMarker = builder.mark
        builder.getTokenType match {
          case ScalaTokenTypes.tAT => {
            builder.advanceLexer //Ate @
            backupMarker.drop
            if (!Pattern3.parse(builder)) {
              idMarker.rollbackTo
              pattern2Marker.done(ScalaElementTypes.REFERENCE_PATTERN)
              val err = builder.mark
              builder.advanceLexer
              err.error(ErrMsg("wrong.pattern"))
            } else {
              idMarker.drop
              pattern2Marker.done(ScalaElementTypes.NAMING_PATTERN)
            }
            return true
          }
          case _ => {
            idMarker.drop
            backupMarker.rollbackTo
          }
        }
      }
      case _ => {
        backupMarker.drop
      }
    }
    pattern2Marker.drop
    Pattern3.parse(builder)
  }
}