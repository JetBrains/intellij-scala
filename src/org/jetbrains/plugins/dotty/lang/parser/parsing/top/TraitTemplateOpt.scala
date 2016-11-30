package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.top.template.{MixinParents, TemplateBody}

/**
  * @author adkozlov
  */
object TraitTemplateOpt extends org.jetbrains.plugins.scala.lang.parser.parsing.top.TraitTemplateOpt {
  override protected def templateBody = TemplateBody
  override protected def earlyDef = EarlyDef
  override protected def mixinParents = MixinParents
}
