package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

object ScImportExprAnnotator extends ElementAnnotator[ScImportExpr] {

  override def annotate(element: ScImportExpr, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (element.qualifier.isEmpty) {
      val annotation = holder.createErrorAnnotation(element.getTextRange,
        ScalaBundle.message("import.expr.should.be.qualified"))
      annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
    }
  }
}
