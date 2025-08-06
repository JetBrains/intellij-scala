package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

object ScTypeAliasAnnotator extends ElementAnnotator[ScTypeAlias] with DumbAware {
  override def annotate(element: ScTypeAlias, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit = {
    // We don't decompile right-hand sides of opaque types but add 'opaque' to abstract types, and should still treat them as such.
    // Thus, we don't emit parser errors and use the annotator (see SCL-23906 for more details).
    if (!element.isDefinition && element.hasModifierProperty("opaque")) {
      val opaqueModifier = element.getModifierList.children.find(_.getText == "opaque").getOrElse(throw new IllegalStateException("Cannot find opaque modifier"))
      holder.createErrorAnnotation(opaqueModifier, ScalaBundle.message("opaque.type.must.have.right-hand.side"))
    }
  }
}
