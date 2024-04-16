package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt

object ScExportStmtAnnotator extends ElementAnnotator[ScExportStmt] {
  override def annotate(
    element:   ScExportStmt,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = element match {
    case _ childOf (ext: ScExtension) =>
      element.importExprs.foreach { expr =>
        for {
          qual       <- expr.qualifier
          qualTarget <- qual.bind()
          if !qualTarget.extensionContext.contains(ext)
        } holder.createErrorAnnotation(
          qual,
          ScalaBundle.message("export.qualifier.not.parameterless.companion.method", qual.getText)
        )
      }
    case _ => ()
  }
}
