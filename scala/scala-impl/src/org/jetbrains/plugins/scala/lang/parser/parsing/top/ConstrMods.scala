package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */
object ConstrMods {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    if (!builder.newlineBeforeCurrentToken) {
      AccessModifier.parse(builder)
    }
    marker.done(ScalaElementType.MODIFIERS)
    true
  }
}
