package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.top.template.{ClassParents, TemplateBody}

/**
  * @author adkozlov
  */
object ClassTemplateOpt extends org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassTemplateOpt {
  override protected val templateBody = TemplateBody
  override protected val classParents = ClassParents
  override protected val earlyDef = EarlyDef
}
