package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMethodCall
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

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
              val missed = for (MissedParameter(p) <- r.problems) yield p.name + ": " + p.paramType.presentableText
              
              if(!missed.isEmpty) holder.createErrorAnnotation(call.args, "Unspecified value parameters: " + missed.mkString(", "))
              
              r.problems.foreach {
                case DoesNotTakeParameters() => 
                  holder.createErrorAnnotation(call.args, f.name + " does not take parameters")
                case ExcessArgument(argument) => 
                  holder.createErrorAnnotation(argument, "Too many arguments for method " + nameOf(f))
                case TypeMismatch(expression, expectedType) => 
                  for(t <- expression.getType(TypingContext.empty)) {
                    holder.createErrorAnnotation(expression, 
                      "Type mismatch, expected: " + expectedType.presentableText + ", actual: " + t.presentableText) 
                  }
                case MissedParameter(_) => // handled above
                
                case _ => holder.createErrorAnnotation(call.args, "Not applicable to " + signatureOf(f))
              }
            }
            case _ => {
              r.problems.foreach {
                case MissedParametersClause(clause) => 
                  holder.createErrorAnnotation(reference, "Missing arguments for method " + nameOf(f))
                case _ => 
              }              
            }
          }
        }
        case _ =>
      }
    }
  }
  
  private def nameOf(f: ScFunction) = f.name + signatureOf(f)

  private def signatureOf(f: ScFunction): String = {
    if(f.parameters.isEmpty)
      ""
    else
      f.paramClauses.clauses.map(clause => format(clause.parameters, clause.paramTypes)).mkString
  }

  private def format(parameters: Seq[ScParameter], types: Seq[ScType]) = {
    val parts = parameters.zip(types).map {
      case (p, t) => t.presentableText + (if(p.isRepeatedParameter) "*" else "")
    }
    "(" + parts.mkString(", ") + ")"
  }
}