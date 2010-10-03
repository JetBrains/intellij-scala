package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import lang.psi.api.expr.ScTypedStmt
import AnnotatorUtils._


trait TypedStatementAnnotator {
  def annotateTypedStatement(typedStatement: ScTypedStmt, holder: AnnotationHolder, highlightErrors: Boolean) {
    if (highlightErrors) {
      for(element <- typedStatement.typeElement)
        checkConformance(typedStatement.expr, element, holder)
    }
  }
}
