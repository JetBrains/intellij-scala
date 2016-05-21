package org.jetbrains.plugins.dotty.lang.parser.parsing.top

import org.jetbrains.plugins.dotty.lang.parser.parsing.top.template.{ClassParents, TemplateBody}
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder
import org.jetbrains.plugins.scala.lang.parser.parsing.top.TraitTemplateOpt

/**
  * @author adkozlov
  */
object TemplateOpt extends org.jetbrains.plugins.scala.lang.parser.parsing.top.ClassTemplateOpt with TraitTemplateOpt {
  override protected val templateBody = TemplateBody
  override protected val classParents = ClassParents
  override protected val earlyDef = EarlyDef
  override protected val mixinParents = MixinParents

  override def parse(builder: ScalaPsiBuilder) = super[ClassTemplateOpt].parse(builder)
}
