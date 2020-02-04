package org.jetbrains.plugins.scala
package lang
package parser
package parsing


import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotations
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ObjectDef

/**
 * @author ilyas
 */
object PackageObject {

  import lexer.ScalaTokenType.ObjectKeyword
  import lexer.ScalaTokenTypes.kPACKAGE

  def parse(builder: ScalaPsiBuilder) : Boolean = {
    val marker = builder.mark
    marker.setCustomEdgeTokenBinders(ScalaTokenBinders.PRECEDING_COMMENTS_TOKEN, null)

    //empty annotations
    Annotations.parseEmptyAndBindLeft()(builder)

    //empty modifiers
    val modifierMarker = builder.mark
    modifierMarker.done(ScalaElementType.MODIFIERS)

    if (builder.getTokenType != kPACKAGE) {
      marker.drop()
      return false
    }
    // Eat `package modifier'
    builder.advanceLexer()

    if (builder.getTokenType != ObjectKeyword) {
      marker.drop()
      return false
    }
    // Eat `object' modifier
    builder.advanceLexer()

    if (ObjectDef parse builder) {
      marker.done(ScalaElementType.ObjectDefinition)
    } else {
      marker.drop()
    }
    true
  }
}