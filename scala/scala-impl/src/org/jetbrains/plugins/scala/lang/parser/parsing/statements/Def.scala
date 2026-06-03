package org.jetbrains.plugins.scala.lang.parser.parsing.statements

import org.jetbrains.plugins.scala.lang.parser.{ScalaElementType, ScalaTokenBinders}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.base.{End, Modifier}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotations
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TmplDef

/**
 * [[Def]] ::= {{{
 *             [Annotations {Modifier}]
 *             'val' ValDef
 *           | 'var' VarDef
 *           | 'def' FunDef
 *           | 'def' MacroDef
 *           | 'type' {nl} TypeDef
 *           | TmplDef
 * }}}
 */
object Def extends ParsingRule {

  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType._
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val defMarker = builder.mark()
    defMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEDING_COMMENTS_TOKEN, null)
    Annotations.parseAndBindToLeft()(builder)

    val modifierMarker = builder.mark()

    def parseScalaMetaInline(): Boolean = builder.isMetaEnabled && builder.tryParseSoftKeyword(InlineKeyword)
    while (Modifier() || parseScalaMetaInline()) {}
    modifierMarker.done(ScalaElementType.MODIFIERS)

    //Look for val,var,def or type
    builder.getTokenType match {
      case `kVAL` =>
        builder.advanceLexer() //Ate val
        if (PatDef()) {
          End()
          defMarker.done(ScalaElementType.PATTERN_DEFINITION)
          true
        }
        else {
          defMarker.rollbackTo()
          false
        }
      case `kVAR` =>
        builder.advanceLexer() //Ate var
        if (PatDef()) {
          End()
          defMarker.done(ScalaElementType.VARIABLE_DEFINITION)
          true
        }
        else {
          defMarker.rollbackTo()
          false
        }
      case `kDEF` =>
        if (MacroDef()) {
          End()
          defMarker.done(ScalaElementType.MACRO_DEFINITION)
          true
        } else if (FunDef()) {
          End()
          defMarker.done(ScalaElementType.FUNCTION_DEFINITION)
          true
        } else {
          defMarker.rollbackTo()
          false
        }
      case `kTYPE` =>
        if (TypeDef()) {
          defMarker.done(ScalaElementType.TYPE_DEFINITION)
          true
        } else if (builder.isScala3 && TypeDcl()) {
          defMarker.done(ScalaElementType.TYPE_DECLARATION)
          true
        } else {
          defMarker.rollbackTo()
          false
        }
      case `kCASE` | IsTemplateDefinition() =>
        defMarker.rollbackTo()
        TmplDef()
      case _ =>
        defMarker.rollbackTo()
        false
    }
  }
}

