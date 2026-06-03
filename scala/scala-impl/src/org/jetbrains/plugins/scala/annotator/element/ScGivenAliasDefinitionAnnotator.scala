package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.parser.ErrMsg
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGivenAliasDefinition, ScTrait}

object ScGivenAliasDefinitionAnnotator extends ElementAnnotator[ScGivenAliasDefinition] {
  override def annotate(
    element: ScGivenAliasDefinition,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    val ownerCls = element.getContainingClass

    if (!ownerCls.is[ScTrait] && element.isDeferred)
      holder.createErrorAnnotation(element, ErrMsg("deferred.inside.class"))
  }
}
