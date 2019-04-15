package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.template.PrivateBeanProperty
import org.jetbrains.plugins.scala.lang.macros.expansion.RecompileAnnotationAction
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.extensions._

import scala.meta.intellij.MetaExpansionsManager


trait ScAnnotationAnnotator extends Annotatable { self: ScAnnotation =>

  override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    checkAnnotationType(holder)
    checkMetaAnnotation(holder)
    PrivateBeanProperty.annotate(this, holder)
  }

  private def checkAnnotationType(holder: AnnotationHolder) {
    //TODO: check annotation is inheritor for class scala.Annotation
  }

  private def checkMetaAnnotation(holder: AnnotationHolder): Unit = {
    import ScalaProjectSettings.ScalaMetaMode

    import scala.meta.intellij.psi._
    if (self.isMetaMacro) {
      if (!MetaExpansionsManager.isUpToDate(this)) {
        val warning = holder.createWarningAnnotation(this, ScalaBundle.message("scala.meta.recompile"))
        warning.registerFix(new RecompileAnnotationAction(this))
      }
      val result = self.parent.flatMap(_.parent) match {
        case Some(ah: ScAnnotationsHolder) => ah.metaExpand
        case _ => Right("")
      }
      val settings = ScalaProjectSettings.getInstance(getProject)
      result match {
        case Left(errorMsg) if settings.getScalaMetaMode == ScalaMetaMode.Enabled =>
          holder.createErrorAnnotation(this, ScalaBundle.message("scala.meta.expandfailed", errorMsg))
        case _ =>
      }
    }
  }
}
