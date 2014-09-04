package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTypedStmt


trait TypedStatementAnnotator {
  def annotateTypedStatement(typedStatement: ScTypedStmt, holder: AnnotationHolder, highlightErrors: Boolean) {
    if (highlightErrors) {
      for(element <- typedStatement.typeElement)
        checkConformance(typedStatement.expr, element, holder)
    }
  }
}
