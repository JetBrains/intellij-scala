package org.jetbrains.plugins.scala
package annotator
package element

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr

object ScImportExprAnnotator extends ElementAnnotator[ScImportExpr] {

  override def annotate(element: ScImportExpr, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (element.qualifier.isEmpty) {
      holder.createErrorAnnotation(element, ScalaBundle.message("import.expr.should.be.qualified"))
    }
  }
}
