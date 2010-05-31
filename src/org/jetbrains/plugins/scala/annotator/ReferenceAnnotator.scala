package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * Pavel.Fatin, 31.05.2010
 */

trait ReferenceAnnotator {
  def annotateReference(reference: ScReferenceElement, holder: AnnotationHolder) {
    for (result <- reference.multiResolve(false);
         r = result.asInstanceOf[ScalaResolveResult];
         if !r.isApplicable) {
      r.element match {
        case f: ScFunction => {
          reference.getContext match {
            case call: ScMethodCall => {
              if(f.paramClauses.clauses.isEmpty && call.args.invocationCount > 0) {
                holder.createErrorAnnotation(call.args, f.name + " does not take parameters")
              } else {
                if(f.parameters.size < call.args.exprs.size) {
                  holder.createErrorAnnotation(call.args, "Too many arguments for method " + f.fullName)
                } else {
                  holder.createErrorAnnotation(call.args, "Not applicable to " + f.signature)
                }
              }
            }
            case _ => {
              if(!f.parameters.isEmpty) {
                holder.createErrorAnnotation(reference, "Missing arguments for method " + f.fullName)
              }
            }
          }
        }
        case _ =>
      }
    }
  }
}