package org.jetbrains.plugins.scala
package lang
package parser
package parsing


import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.ObjectDef

/**
 * @author ilyas
 */
object PackageObject extends PackageObject {
  override protected val objectDef = ObjectDef
}

trait PackageObject {
  protected val objectDef: ObjectDef

  def parse(builder: ScalaPsiBuilder) : Boolean = {
    val marker = builder.mark
    //empty annotations
     val annotationsMarker = builder.mark
    annotationsMarker.done(ScalaElementTypes.ANNOTATIONS)
    //empty modifiers
    val modifierMarker = builder.mark
    modifierMarker.done(ScalaElementTypes.MODIFIERS)

    if (builder.getTokenType != ScalaTokenTypes.kPACKAGE) {
      marker.drop()
      return false
    }
    // Eat `package modifier'
    builder.advanceLexer()

    if (builder.getTokenType != ScalaTokenTypes.kOBJECT) {
      marker.drop()
      return false
    }
    // Eat `object' modifier
    builder.advanceLexer()

    if (objectDef parse builder) {
      marker.done(ScalaElementTypes.OBJECT_DEF)
    } else {
      marker.drop()
    }
    true
  }
}