package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrBlock

object ScConstrBlockAnnotator extends ElementAnnotator[ScConstrBlock] {

  override def annotate(constrBlock: ScConstrBlock, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    if (constrBlock.selfInvocation.isEmpty) {
      constrBlock.getContainingFile match {
        case file: ScalaFile if !file.isCompiled =>
          def startRange = TextRange.from(constrBlock.startOffset, 1)
          def afterBraceRange = constrBlock.getLBrace.map(lb => TextRange.from(lb.endOffset, 1))
          def firstStmtRange = constrBlock.statements.headOption.map(_.getTextRange)
          val highlightRange = firstStmtRange.orElse(afterBraceRange).getOrElse(startRange)
          holder.createErrorAnnotation(highlightRange, ScalaBundle.message("constructor.invocation.expected"))
        case _ => //nothing to do in decompiled stuff
      }
    }
  }
}

