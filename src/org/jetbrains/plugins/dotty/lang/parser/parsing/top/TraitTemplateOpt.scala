package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.top.template.TemplateBody

/**
  * @author adkozlov
  */
object TraitTemplateOpt extends org.jetbrains.plugins.scala.lang.parser.parsing.top.TraitTemplateOpt {
  override protected val templateBody = TemplateBody
  override protected val mixinParents = MixinParents
  override protected val earlyDef = EarlyDef
}
