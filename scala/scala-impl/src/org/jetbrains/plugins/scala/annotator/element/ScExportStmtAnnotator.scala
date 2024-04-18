package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.{ObjectExt, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtensionBody, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScExportStmt

object ScExportStmtAnnotator extends ElementAnnotator[ScExportStmt] {
  override def annotate(
    element:   ScExportStmt,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = element match {
    case _ childOf (ext: ScExtensionBody) =>
      element.importExprs.foreach { expr =>
        for {
          qual       <- expr.qualifier
          qualTarget <- qual.bind()
          fun = qualTarget.element
          isParameterless = fun.asOptionOf[ScFunction].exists(f => f.isParameterless && !f.hasTypeParameters)
          if !isParameterless || (fun.getContext ne ext)
        } holder.createErrorAnnotation(
          qual,
          ScalaBundle.message("export.qualifier.not.parameterless.companion.method", qual.getText)
        )
      }
    case _ => ()
  }
}
