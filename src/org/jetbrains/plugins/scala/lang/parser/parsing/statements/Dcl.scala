package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.PsiBuilder

import com.intellij.psi._
import com.intellij.lang.ParserDefinition
import org.jetbrains.plugins.scala.ScalaFileType
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiManager

import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements.ScTemplateStatement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Ids
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.Param
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.VariantTypeParam
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.TypeParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClauses
import org.jetbrains.plugins.scala.lang.parser.parsing.top.params.ParamClause
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.ArgumentExprs
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockStat
import org.jetbrains.plugins.scala.lang.parser.parsing.patterns.Pattern2
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions._
import org.jetbrains.plugins.scala.lang.parser.parsing.ConstrUnpredict
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 11.02.2008
* Time: 17:58:07
* To change this template use File | Settings | File Templates.
*/

/*
 * Dcl ::= [{Annotation} {Modifier}]
 *          ('val' ValDcl
 *         | 'var' VarDcl
 *         | 'def' FunDcl
 *         | 'type' {nl} TypeDcl)
 */

object Dcl {
  def parse(builder: PsiBuilder): Boolean = parse(builder,true)
  def parse(builder: PsiBuilder, isMod: Boolean): Boolean = {
    val dclMarker = builder.mark
    if (isMod) {
      //TODO: parse annotations
      //parse modifiers
      val modifierMarker = builder.mark
      var isModifier = false
      while (BNF.firstModifier.contains(builder.getTokenType)) {
        Modifier.parse(builder)
        isModifier = true
      }
      modifierMarker.done(ScalaElementTypes.MODIFIERS)
    }
    //Look for val,var,def or type
    builder.getTokenType match {
      case ScalaTokenTypes.kVAL => {
        if (ValDcl parse builder) {
          dclMarker.done(ScalaElementTypes.VALUE_DECLARATION)
          return true
        }
        else {
          dclMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kVAR => {
        if (VarDcl parse builder) {
          dclMarker.done(ScalaElementTypes.VARIABLE_DECLARATION)
          return true
        }
        else {
          dclMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kDEF => {
        if (FunDcl parse builder) {
          dclMarker.done(ScalaElementTypes.FUNCTION_DECLARATION)
          return true
        }
        else {
          dclMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kTYPE => {
        if (TypeDcl parse builder) {
          dclMarker.done(ScalaElementTypes.TYPE_DECLARATION)
          return true
        }
        else {
          dclMarker.rollbackTo
          return false
        }
      }
      case _ => {
        dclMarker.rollbackTo
        return false
      }
    }
  }
}