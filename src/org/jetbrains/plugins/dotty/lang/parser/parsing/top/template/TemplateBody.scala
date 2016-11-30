package org.jetbrains.plugins.dotty.lang.parser.parsing.top.template

import org.jetbrains.plugins.dotty.lang.parser.parsing.types.SelfType

/**
  * @author adkozlov
  */
object TemplateBody extends org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody {
  override protected def templateStat = TemplateStat
  override protected def selfType = SelfType
}
