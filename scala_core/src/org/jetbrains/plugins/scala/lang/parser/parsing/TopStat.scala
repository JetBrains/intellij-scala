package org.jetbrains.plugins.scala.lang.parser.parsing

import com.intellij.codeInsight.template.impl.TemplateState
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import top.template.TemplateStat

/** 
* @author Alexander Podkhalyuzin
* Date: 05.02.2008
*/

/*
*  TopStat ::= {Annotation} {Modifier} -> TmplDef (it's mean that all parsed in TmplDef)
*            | Import
*            | Packaging
*/

object TopStat {
  def parse(builder: PsiBuilder, state: Int): Int = {
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPORT => {
        Import parse builder
        ParserState.ADDITIONAL_STATE
      }
      case ScalaTokenTypes.kPACKAGE => {
        if (state == 2) ParserState.EMPTY_STATE
        else {
          if (Packaging parse builder) ParserState.FILE_STATE
          else ParserState.EMPTY_STATE
        }
      }
      case _ => {
        state match {
          case ParserState.EMPTY_STATE => if (!TmplDef.parse(builder)) {
              if (!TemplateStat.parse(builder)) ParserState.EMPTY_STATE
              else ParserState.SCRIPT_STATE
            } else ParserState.FILE_STATE
          case ParserState.FILE_STATE => if (!TmplDef.parse(builder)) ParserState.EMPTY_STATE
            else ParserState.FILE_STATE
          case ParserState.SCRIPT_STATE => if (!TemplateStat.parse(builder)) ParserState.EMPTY_STATE
            else ParserState.SCRIPT_STATE
        }
      }
    }
  }
}