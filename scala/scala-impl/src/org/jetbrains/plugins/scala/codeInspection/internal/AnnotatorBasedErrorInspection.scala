package org.jetbrains.plugins.scala
package codeInspection
package internal

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.{HighlightInfoHolder, HighlightVisitorImpl}
import com.intellij.codeInspection._
import com.intellij.lang.annotation._
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiElement, PsiElementVisitor, PsiJavaFile}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.{DummyScalaAnnotationBuilder, ScalaAnnotationBuilder, ScalaAnnotationHolder, ScalaAnnotator}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

final class AnnotatorBasedErrorInspection extends LocalInspectionTool {

  import AnnotatorBasedErrorInspection._

  //noinspection TypeAnnotation
  override def buildVisitor(holder: ProblemsHolder,
                            isOnTheFly: Boolean) = new PsiElementVisitor {

    override def visitElement(element: PsiElement): Unit = {
      implicit val project: Project = element.getProject

      element.getContainingFile match {
        case javaFile: PsiJavaFile => highlightJavaElement(element, javaFile, holder)
        case scalaFile: ScalaFile if !InjectedLanguageManager.getInstance(project).isInjectedFragment(scalaFile) => // todo: remove this after proper support of scala fragments in .md files
          val annotator = new ScalaAnnotator() {
            override def isAdvancedHighlightingEnabled(element: PsiElement): Boolean = true
          }
          annotator.annotate(element)(new DummyAnnotationHolder(element, holder))
        case _ =>
      }
    }
  }
}

object AnnotatorBasedErrorInspection {

  import ProblemHighlightType.ERROR

  private def highlightJavaElement(element: PsiElement,
                                   javaFile: PsiJavaFile,
                                   holder: ProblemsHolder)
                                  (implicit project: Project): Unit = {
    val highlightInfoHolder = new HighlightInfoHolder(javaFile)

    for {
      visitor <- HighlightVisitor.EP_HIGHLIGHT_VISITOR
        .getExtensions(project)
        .headOption

      if visitor.isInstanceOf[HighlightVisitorImpl]
      cloned = visitor.asInstanceOf[HighlightVisitorImpl].clone
    } cloned.analyze(
      javaFile,
      true,
      highlightInfoHolder,
      () => visitor.visit(element)
    )

    if (highlightInfoHolder.hasErrorResults) {
      holder.registerProblem(
        element,
        ScalaInspectionBundle.message("error.detected"),
        ERROR,
        null: TextRange
      )
    }
  }

  private class DummyAnnotationHolder(element: PsiElement, holder: ProblemsHolder) extends ScalaAnnotationHolder {

    override def isBatchMode: Boolean = false

    override def getCurrentAnnotationSession: AnnotationSession = {
      new AnnotationSession(element.getContainingFile)
    }

    override def newAnnotation(severity: HighlightSeverity, message: String): ScalaAnnotationBuilder =
      new MyAnnotationBuilder(severity, message)


    override def newSilentAnnotation(severity: HighlightSeverity): ScalaAnnotationBuilder =
      new MyAnnotationBuilder(severity)

    private class MyAnnotationBuilder(severity: HighlightSeverity, message: String = null)
      extends DummyScalaAnnotationBuilder(severity, message) {

      override def onCreate(severity: HighlightSeverity, @Nls message: String, range: TextRange): Unit = {
        val rangeInElement = range.shiftLeft(element.startOffset)
        holder.registerProblem(element, rangeInElement, message)
      }

    }

  }
}