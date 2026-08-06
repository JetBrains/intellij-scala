package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelectors}

object ScImportExprAnnotator extends ElementAnnotator[ScImportExpr] {

  override def annotate(element: ScImportExpr, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (element.qualifier.isEmpty && !isScala3StyleAliasImport(element)) {
      holder.createErrorAnnotation(element, ScalaBundle.message("import.expr.should.be.qualified"))
    }
  }

  private[this] def isScala3StyleAliasImport(element: ScImportExpr): Boolean = element.selectorSet match {
    case Some(ScImportSelectors(selector)) =>
      selector.isScala3StyleAliasImport
    case _ => false
  }
}
