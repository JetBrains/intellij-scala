package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.quickfix.ChangeTypeFix
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScCatchBlock, ScTry}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.{Compatibility, ScType, api}
import org.jetbrains.plugins.scala.lang.psi.types.api.{Boolean, ScTypePresentation}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils._

import scala.collection.Seq


trait ScCatchBlockAnnotator extends Annotatable { self: ScCatchBlock =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    expression match {
      case Some(expr) =>
        val tp = expr.`type`().getOrAny
        val throwable = ScalaPsiManager.instance(expr.getProject).getCachedClass(expr.resolveScope, "java.lang.Throwable").orNull
        if (throwable == null) return
        val throwableType = ScDesignatorType(throwable)
        def checkMember(memberName: String, checkReturnTypeIsBoolean: Boolean) {
          val processor = new MethodResolveProcessor(expr, memberName, List(Seq(new Compatibility.Expression(throwableType))),
            Seq.empty, Seq.empty)
          processor.processType(tp, expr)
          val candidates = processor.candidates
          if (candidates.length != 1) {
            val error = ScalaBundle.message("method.is.not.member", memberName, tp.presentableText)
            val annotation = holder.createErrorAnnotation(expr, error)
            annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
          } else if (checkReturnTypeIsBoolean) {
            val maybeType = candidates(0) match {
              case ScalaResolveResult(fun: ScFunction, subst) => fun.returnType.map(subst).toOption
              case _ => None
            }

            if (!maybeType.exists(_.equiv(Boolean))) {
              val error = ScalaBundle.message("expected.type.boolean", memberName)
              val annotation = holder.createErrorAnnotation(expr, error)
              annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
            }
          } else {
            getContext match {
              case t: ScTry =>
                t.expectedTypeEx(fromUnderscore = false) match {
                  case Some((tp: ScType, _)) if tp equiv api.Unit => //do nothing
                  case Some((tp: ScType, typeElement)) =>
                    val returnType = candidates(0) match {
                      case ScalaResolveResult(fun: ScFunction, subst) => fun.returnType.map(subst)
                      case _ => return
                    }
                    val conformance = smartCheckConformance(Right(tp), returnType)
                    if (!conformance) {
                      if (typeAware) {
                        val (retTypeText, expectedTypeText) = ScTypePresentation.different(returnType.getOrNothing, tp)
                        val error = ScalaBundle.message("expr.type.does.not.conform.expected.type", retTypeText, expectedTypeText)
                        val annotation = holder.createErrorAnnotation(expr, error)
                        typeElement match {
                          //Don't highlight te if it's outside of original file.
                          case Some(te) if te.containingFile == t.containingFile =>
                            val fix = new ChangeTypeFix(te, returnType.getOrNothing)
                            annotation.registerFix(fix)
                            val teAnnotation = annotationWithoutHighlighting(holder, te)
                            teAnnotation.registerFix(fix)
                          case _ =>
                        }
                      }
                    }
                  case _ => //do nothing
                }
              case _ =>
            }
          }
        }
        checkMember("isDefinedAt", checkReturnTypeIsBoolean = true)
        checkMember("apply", checkReturnTypeIsBoolean = false)
      case _ =>
    }
  }
}
