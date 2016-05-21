package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.top.template.TemplateBody

/**
  * @author adkozlov
  */
object ClassTemplate extends org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassTemplate {
  override protected val earlyDef = EarlyDef
  override protected val classParents = Parents
  override protected val templateBody = TemplateBody
}
