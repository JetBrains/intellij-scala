package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IChameleonElementType
import com.intellij.psi.tree.TokenSet

import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateParents
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.VariantTypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.base.ModifierWithoutImplicit
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Expr
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern2

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 06.02.2008
* Time: 15:32:21
* To change this template use File | Settings | File Templates.
*/

/*
 * PatDef ::= Pattern2 {',' Pattern2} [':' Type] '=' Expr
 */
//TODO: Rewrite this
object PatDef {
  def parse(builder: PsiBuilder): Boolean = {
    val pattern2sMarker = builder.mark

      if (BNF.firstPattern2.contains(builder.getTokenType)){
          (new Pattern2()).parse(builder)
      } else {
        builder error "pattern expected"
        pattern2sMarker.rollbackTo()
        return false
      }

      var numberOfPattern2s = 1;

      while (ScalaTokenTypes.tCOMMA.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tCOMMA)

        if (BNF.firstPattern2.contains(builder.getTokenType)){
            (new Pattern2()).parse(builder)
          numberOfPattern2s = numberOfPattern2s + 1;

        } else {
          builder error "pattern expected"
          pattern2sMarker.rollbackTo()
          return false
        }
      }

      if (numberOfPattern2s > 1) pattern2sMarker.done(ScalaElementTypes.PATTERN2_LIST)
      else pattern2sMarker.drop

      var hasTypeDcl = false

      if (ScalaTokenTypes.tCOLON.equals(builder.getTokenType)) {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tCOLON)

        if (BNF.firstType.contains(builder.getTokenType)) {
          Type parse builder
        } else {
          builder error "type declaration expected"
        }

        hasTypeDcl = true
      }
     if (! ScalaTokenTypes.tASSIGN.equals(builder.getTokenType)) {
        pattern2sMarker.rollbackTo
        return false
      } else {
        ParserUtils.eatElement(builder, ScalaTokenTypes.tASSIGN)

        if (BNF.firstExpr.contains(builder.getTokenType)) {
          Expr parse builder
        } else {
          builder error "wrong start of expression"
          pattern2sMarker.rollbackTo
          return false
        }
        return true
      }

      return false
    }
  }