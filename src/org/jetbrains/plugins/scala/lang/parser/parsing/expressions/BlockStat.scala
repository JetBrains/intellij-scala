package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import _root_.scala.collection.mutable._

import com.intellij.lang.PsiBuilder, org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.util._
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types._
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns._
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template._
import org.jetbrains.plugins.scala.lang.parser.parsing.base._
import org.jetbrains.plugins.scala.lang.parser.parsing.top._
import org.jetbrains.plugins.scala.lang.parser.parsing.statements._

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.03.2008
* Time: 11:41:01
* To change this template use File | Settings | File Templates.
*/

/*
 * BlockStat ::= Import
 *             | ['implicit'] Def
 *             | {LocalModifier} TmplDef
 *             | Expr1
 */

object BlockStat {
  def parse(builder: PsiBuilder) : Boolean = {
    Block.flag = false
    val blockStatMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kIMPORT => {
        Import parse builder
        blockStatMarker.done(ScalaElementTypes.BLOCK_STAT)
        return true
      }
      case _ => {}
    }
    if (!Def.parse(builder,false,true)) {
      if (!TmplDef.parse(builder)) {
        Block.flag = true
        if (!Expr1.parse(builder)) {
          blockStatMarker.drop
          return false
        }
      }
    }
    blockStatMarker.done(ScalaElementTypes.BLOCK_STAT)
    return true
  }
}