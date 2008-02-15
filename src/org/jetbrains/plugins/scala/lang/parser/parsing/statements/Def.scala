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
* Time: 19:33:32
* To change this template use File | Settings | File Templates.
*/

/*
 * Def ::= [{Annotation} {Modifier}]
 *          ('val' ValDef
 *         | 'var' VarDef
 *         | 'def' FunDef
 *         | 'type' {nl} TypeDef)
 */

object Def {
  def parse(builder: PsiBuilder): Boolean = parse(builder,true)
  def parse(builder: PsiBuilder, isMod: Boolean): Boolean = {
    val defMarker = builder.mark
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
        builder.advanceLexer //Ate val
        if (PatDef parse builder) {
          defMarker.done(ScalaElementTypes.PATTERN_DEFINITION)
          return true
        }
        else {
          defMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kVAR => {
        builder.advanceLexer //Ate var
        if (VarDef parse builder) {
          defMarker.done(ScalaElementTypes.VARIABLE_DEFINITION)
          return true
        }
        else {
          defMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kDEF => {
        if (FunDef parse builder) {
          defMarker.done(ScalaElementTypes.FUNCTION_DEFINITION)
          return true
        }
        else {
          defMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kTYPE => {
        if (TypeDef parse builder) {
          defMarker.done(ScalaElementTypes.TYPE_DEFINITION)
          return true
        }
        else {
          defMarker.rollbackTo
          return false
        }
      }
      case ScalaTokenTypes.kCASE | ScalaTokenTypes.kCLASS
      | ScalaTokenTypes.kOBJECT | ScalaTokenTypes.kTRAIT => {
        defMarker.rollbackTo
        if (TmplDef parse builder) {
          return true
        }
        else {
          return false
        }
      }
      case _ => {
        defMarker.rollbackTo
        return false
      }
    }
  }
}

