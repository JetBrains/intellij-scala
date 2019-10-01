package org.jetbrains.plugins.scala
package lang
package parser
package parsing


import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ObjectDef

/**
 * @author ilyas
 */
object PackageObject {

  import lexer.ScalaTokenType.ObjectKeyword
  import lexer.ScalaTokenTypes.kPACKAGE

  def parse(builder: ScalaPsiBuilder) : Boolean = {
    val marker = builder.mark
    //empty annotations
     val annotationsMarker = builder.mark
    annotationsMarker.done(ScalaElementType.ANNOTATIONS)
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
      marker.done(ScalaElementType.OBJECT_DEFINITION)
    } else {
      marker.drop()
    }
    true
  }
}