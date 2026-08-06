package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.Annotations

/**
 * [[ConstrMods]] ::= [[Annotations]] [ [[AccessModifier]] ]
 */
object ConstrMods extends ParsingRule {

  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    Annotations.parseForConstructor()

    val modifiersMarker = builder.mark()
    if (!builder.newlineBeforeCurrentToken) {
      AccessModifier()
    }
    modifiersMarker.done(ScalaElementType.MODIFIERS)

    true
  }
}
