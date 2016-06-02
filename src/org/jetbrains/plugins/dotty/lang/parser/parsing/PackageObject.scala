package org.jetbrains.plugins.dotty.lang.parser.parsing

import org.jetbrains.plugins.dotty.lang.parser.DottyElementTypes
import org.jetbrains.plugins.dotty.lang.parser.parsing.top.ObjectDef

/**
  * @author adkozlov
  */
object PackageObject extends org.jetbrains.plugins.scala.lang.parser.parsing.PackageObject {
  override protected val objectDef = ObjectDef

  override protected val elementTypes = DottyElementTypes
}
