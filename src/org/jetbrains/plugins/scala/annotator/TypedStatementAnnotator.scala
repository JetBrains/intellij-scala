package org.jetbrains.plugins.scala
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTypedStmt
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem


trait TypedStatementAnnotator {
  def annotateTypedStatement(typedStatement: ScTypedStmt, holder: AnnotationHolder, highlightErrors: Boolean)
                            (implicit typeSystem: TypeSystem = typedStatement.typeSystem) {
    if (highlightErrors) {
      for(element <- typedStatement.typeElement)
        checkConformance(typedStatement.expr, element, holder)
    }
  }
}
