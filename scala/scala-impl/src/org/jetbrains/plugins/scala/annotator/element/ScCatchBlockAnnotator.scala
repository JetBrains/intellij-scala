package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils._
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.quickfix.ChangeTypeFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScCatchBlock, ScTry}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.Boolean
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.{Compatibility, ScType, api}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor

//https://youtrack.jetbrains.com/issue/SCL-3328
object ScCatchBlockAnnotator extends ElementAnnotator[ScCatchBlock] {

  override def annotate(element: ScCatchBlock, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {

    implicit val project: Project = element.getProject

    element.expression match {
      case Some(expr) =>
        val tp = expr.`type`().getOrAny
        val throwable = ScalaPsiManager.instance(expr.getProject).getCachedClass(expr.resolveScope, "java.lang.Throwable").orNull
        if (throwable == null) return
        val throwableType = ScDesignatorType(throwable)
        def checkMember(memberName: String, checkReturnTypeIsBoolean: Boolean): Unit = {
          val processor = new MethodResolveProcessor(expr, memberName, List(Seq(Compatibility.Expression(throwableType))),
            Seq.empty, Seq.empty)
          processor.processType(tp, expr)
          val candidates = processor.candidates
          if (candidates.length != 1) {
            val error = ScalaBundle.message("method.is.not.member", memberName, tp.presentableText(expr))
            holder.createErrorAnnotation(expr, error)
          } else if (checkReturnTypeIsBoolean) {
            val maybeType = candidates(0) match {
              case ScalaResolveResult(fun: ScFunction, subst) => fun.returnType.map(subst).toOption
              case _ => None
            }

            if (!maybeType.exists(_.equiv(Boolean))) {
              val error = ScalaBundle.message("expected.type.boolean", memberName)
              holder.createErrorAnnotation(expr, error)
            }
          } else {
            element.getContext match {
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
                        val (retTypeText, expectedTypeText) = TypePresentation.different(returnType.getOrNothing, tp)(t)
                        val error = ScalaBundle.message("expr.type.does.not.conform.expected.type", retTypeText, expectedTypeText)
                        typeElement match {
                          //Don't highlight te if it's outside of original file.
                          case Some(te) if te.containingFile == t.containingFile =>
                            val fix = new ChangeTypeFix(te, returnType.getOrNothing)
                            holder.newAnnotation(HighlightSeverity.ERROR, error)
                              .range(expr)
                              .withFix(fix)
                              .newFix(fix).range(te.getTextRange).registerFix //quickfix on type element
                              .create()
                          case _ =>
                            holder.createErrorAnnotation(expr, error)
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
