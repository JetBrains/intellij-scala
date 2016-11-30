package org.jetbrains.plugins.dotty.lang.parser.parsing

import org.jetbrains.plugins.dotty.lang.parser.parsing.top.TmplDef
import org.jetbrains.plugins.dotty.lang.parser.parsing.top.template.TemplateStat

/**
  * @author adkozlov
  */
object TopStat extends org.jetbrains.plugins.scala.lang.parser.parsing.TopStat {
  override protected def tmplDef = TmplDef
  override protected def templateStat = TemplateStat
  override protected def packaging = Packaging
  override protected def packageObject = PackageObject
}
