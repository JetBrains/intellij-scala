package org.jetbrains.plugins.scala
package annotator
package element

import org.jetbrains.plugins.scala.annotator.template.PrivateBeanProperty
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.expansion.RecompileAnnotationAction
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.annotation.unused
import scala.meta.intellij.{MetaExpansionsManager, isMetaAnnotationExpansionEnabled}

object ScAnnotationAnnotator extends ElementAnnotator[ScAnnotation] {

  override def annotate(element: ScAnnotation, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    checkAnnotationType(element)
    checkMetaAnnotation(element)
    PrivateBeanProperty.annotate(element)
  }

  private def checkAnnotationType(@unused("will be used after resolving TODO") element: ScAnnotation)
                                 (implicit holder: ScalaAnnotationHolder): Unit = {
    //TODO: check annotation is inheritor for class scala.Annotation
  }

  private def checkMetaAnnotation(element: ScAnnotation)
                                 (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!isMetaAnnotationExpansionEnabled)
      return
    import ScalaProjectSettings.ScalaMetaMode

    import scala.meta.intellij.psi._
    if (element.isMetaMacro) {
      if (!MetaExpansionsManager.isUpToDate(element)) {
        holder.createWarningAnnotation(
          element,
          ScalaBundle.message("scala.meta.recompile"),
          new RecompileAnnotationAction(element)
        )
      }
      val result = element.parent.flatMap(_.parent) match {
        case Some(ah: ScAnnotationsHolder) => ah.metaExpand
        case _ => Right("")
      }
      val settings = ScalaProjectSettings.getInstance(element.getProject)
      result match {
        case Left(error) if settings.getScalaMetaMode == ScalaMetaMode.Enabled =>
          holder.createErrorAnnotation(element, ScalaBundle.message("scala.meta.expandfailed", error.message))
        case _ =>
      }
    }
  }
}
