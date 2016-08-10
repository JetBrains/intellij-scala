package org.jetbrains.plugins.dotty.lang

import org.jetbrains.plugins.dotty.lang.parser.DottyElementTypes
import org.jetbrains.plugins.scala.lang.TokenSets

/**
  * @author adkozlov
  */
object DottyTokenSets extends TokenSets {
  override lazy val elementTypes = DottyElementTypes
}
