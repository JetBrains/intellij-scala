package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package statements

import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.Modifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotations

/**
 * [[Dcl]] ::= [ [[Annotations]] {Modifier}]
 *          ('val' ValDcl
 *         | 'var' VarDcl
 *         | 'def' FunDcl
 *         | 'type' {nl} TypeDcl)
 */
object Dcl {

  def parse(implicit builder: ScalaPsiBuilder): Boolean = apply()
  def apply(isMod: Boolean = true)(implicit builder: ScalaPsiBuilder): Boolean = {
    val dclMarker = builder.mark()
    dclMarker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEDING_COMMENTS_TOKEN, null)
    if (isMod) {
      Annotations.parseAndBindToLeft()(builder)

      //parse modifiers
      val modifierMarker = builder.mark()
      while (Modifier()) {}
      modifierMarker.done(ScalaElementType.MODIFIERS)
    } else {
      //empty annotations
      val annotationsMarker = builder.mark()
      annotationsMarker.done(ScalaElementType.ANNOTATIONS)
      //empty modifiers
      val modifierMarker = builder.mark()
      modifierMarker.done(ScalaElementType.MODIFIERS)
    }
    //Look for val,var,def or type
    val (successfullyParsed, elementType) = builder.getTokenType match {
      case ScalaTokenTypes.kVAL  => (ValDcl(),  ScalaElementType.VALUE_DECLARATION)
      case ScalaTokenTypes.kVAR  => (VarDcl(),  ScalaElementType.VARIABLE_DECLARATION)
      case ScalaTokenTypes.kDEF  => (FunDcl(),  ScalaElementType.FUNCTION_DECLARATION)
      case ScalaTokenTypes.kTYPE => (TypeDcl(), ScalaElementType.TYPE_DECLARATION)
      case _                     => (false, null: IElementType)
    }

    if (successfullyParsed)
      dclMarker.done(elementType)
    else
      dclMarker.rollbackTo()

    successfullyParsed
  }
}