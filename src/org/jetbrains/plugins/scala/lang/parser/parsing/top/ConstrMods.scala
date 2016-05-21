package org.jetbrains.plugins.scala.lang.parser.parsing.top

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.parser.parsing.base.AccessModifier
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/**
  * @author adkozlov
  */
object ConstrMods extends ConstrMods

trait ConstrMods {
  def parse(builder: ScalaPsiBuilder): Boolean = {
    val marker = builder.mark()
    if (!builder.newlineBeforeCurrentToken) {
      parseModifier(builder)
    }
    marker.done(ScalaElementTypes.MODIFIERS)
    true
  }

  protected def parseModifier(builder: ScalaPsiBuilder): Boolean =
    AccessModifier.parse(builder)
}
