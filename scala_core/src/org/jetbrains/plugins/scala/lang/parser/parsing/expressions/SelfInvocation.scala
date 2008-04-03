package org.jetbrains.plugins.scala.lang.parser.parsing.expressions

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import com.intellij.psi._
import com.intellij.lang.ParserDefinition
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiManager

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern2
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier

/** 
* @author Alexander Podkhalyuzin
* Date: 13.02.2008
*/

/*
 * SelfInvocation ::= 'this' ArgumentExprs {ArgumentExprs}
 */

object SelfInvocation {
  def parse(builder: PsiBuilder): Boolean = {
    val selfMarker = builder.mark
    builder.getTokenType match {
      case ScalaTokenTypes.kTHIS => {
        builder.advanceLexer //Ate this
      }
      case _ => {
        builder error ScalaBundle.message("this.expected", new Array[Object](0))
      }
    }
    val argExprsMarker = builder.mark
    var numberOfArgExprs = 0;

    if (BNF.firstArgumentExprs.contains(builder.getTokenType)) {
      ArgumentExprs parse builder
      numberOfArgExprs = 1
    }
    else {
      builder error ScalaBundle.message("arg.expr.expected", new Array[Object](0))
    }

    while (builder.getTokenType == ScalaTokenTypes.tLPARENTHESIS) {
      ArgumentExprs parse builder
      numberOfArgExprs = numberOfArgExprs + 1
    }

    argExprsMarker.drop
    selfMarker.done(ScalaElementTypes.SELF_INVOCATION)
    return true
  }
}