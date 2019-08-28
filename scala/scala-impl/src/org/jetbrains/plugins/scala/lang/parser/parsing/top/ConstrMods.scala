package org.jetbrains.plugins.scala
package lang
package parser
package parsing
package top

import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotation

/**
 * [[ConstrMods]] ::= { [[Annotation]] } [ [[AccessModifier]] ]
 *
 * @author adkozlov
 */
object ConstrMods extends ParsingRule {

  override def parse()(implicit builder: ScalaPsiBuilder): Boolean = {
    annotations()

    val modifiersMarker = builder.mark()
    if (!builder.newlineBeforeCurrentToken) {
      AccessModifier.parse(builder)
    }
    modifiersMarker.done(ScalaElementType.MODIFIERS)

    true
  }

  private def annotations()(implicit builder: ScalaPsiBuilder): Unit = {
    val modifierMarker = builder.mark()
    if (!builder.newlineBeforeCurrentToken) {
      while (Annotation.parse(builder)) {}
    }
    modifierMarker.done(ScalaElementType.ANNOTATIONS)
  }

}
