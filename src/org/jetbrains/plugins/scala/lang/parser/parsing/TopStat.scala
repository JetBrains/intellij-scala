package org.jetbrains.plugins.scala.lang.parser.parsing

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Import
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.types.StableId
import com.intellij.psi.tree.IElementType
import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 05.02.2008
* Time: 16:41:31
* To change this template use File | Settings | File Templates.
*/

/*
*  TopStat ::= {Annotation} {Modifier} -> TmplDef (it's mean that all parsed in TmplDef)
*            | Import
*            | Packaging
*/

object TopStat {
  def parse(builder: PsiBuilder): Boolean = {
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPORT => {
        Import parse builder
        return true
      }
      case ScalaTokenTypes.kPACKAGE => {
        Packaging parse builder
        return true
      }
      case _ => {
        if (TmplDef parse builder)
          return true
        else
          return false
      }
    }
  }
}