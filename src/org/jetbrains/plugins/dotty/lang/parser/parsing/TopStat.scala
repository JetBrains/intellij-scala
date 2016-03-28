package org.jetbrains.plugins.dotty.lang.parser.parsing

import org.jetbrains.plugins.dotty.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.dotty.lang.parser.parsing.top.template.TemplateStat

/**
  * @author adkozlov
  */
object TopStat extends org.jetbrains.plugins.scala.lang.parser.parsing.TopStat {
  override protected val tmplDef = TmplDef
  override protected val templateStat = TemplateStat
  override protected val packaging = Packaging
  override protected val packageObject = PackageObject
}
