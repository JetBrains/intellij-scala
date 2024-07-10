package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions.{ObjectExt, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtensionBody, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportExpr}

object ScExportStmtAnnotator extends ElementAnnotator[ScExportStmt] {
  override def annotate(
    element:   ScExportStmt,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = element match {
    case _ childOf (_: ScExtensionBody) =>
      val importExprs = element.importExprs
      importExprs.foreach(annotateImportExpr(_))
    case _ => ()
  }

  private def annotateImportExpr(expr: ScImportExpr)(implicit holder: ScalaAnnotationHolder): Unit = {
    for {
      qualifier <- expr.qualifier
    } {
      def errorMessage = ScalaBundle.message("export.qualifier.not.parameterless.companion.method", qualifier.getText)

      qualifier.bind() match {
        case Some(qualifierTarget) =>
          val fun = qualifierTarget.element
          val isParameterlessFunction = fun.asOptionOf[ScFunction].exists(f => f.isParameterless && !f.hasTypeParameters)
          if (!isParameterlessFunction) {
            holder.createErrorAnnotation(qualifier, errorMessage)
          }
        case _ =>
          // If the target is located outside the current extension, we don't resolve the error
          // (see ScStableCodeReferenceImpl.processQualifier).
          // But we still show the same error message (as compiler does)
          holder.createErrorAnnotation(qualifier, errorMessage, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
      }
    }
  }
}
