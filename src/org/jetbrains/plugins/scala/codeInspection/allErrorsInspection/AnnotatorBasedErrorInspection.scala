package org.jetbrains.plugins.scala.codeInspection.allErrorsInspection

import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.scala.annotator.ScalaAnnotator
import java.lang.String
import com.intellij.openapi.util.TextRange
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.{HighlightSeverity, Annotation, AnnotationHolder}
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder, LocalInspectionTool}
import com.intellij.lang.annotation.AnnotationSession

/**
 * @author Alexander Podkhalyuzin
 */

class AnnotatorBasedErrorInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.SCALA

  def getDisplayName: String = "All Errors Tool"

  def getShortName: String = "All errors"

  override def isEnabledByDefault: Boolean = false

  override def getStaticDescription: String = "Inspection shows all files in which there are some errors"

  override def getID: String = "AllErrorsTool"

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {

    new PsiElementVisitor {
      override def visitElement(element: PsiElement): Unit = {
        val annotator = new ScalaAnnotator
        val FakeAnnotation = new com.intellij.lang.annotation.Annotation(
          0, 0, HighlightSeverity.INFO, "message", "tooltip")
        val annotationHolder = new AnnotationHolder {
          def createInfoAnnotation(range: TextRange, message: String): Annotation = FakeAnnotation
          def createInfoAnnotation(node: ASTNode, message: String): Annotation = FakeAnnotation
          def createInfoAnnotation(elt: PsiElement, message: String): Annotation = FakeAnnotation
          def createInformationAnnotation(range: TextRange, message: String): Annotation = FakeAnnotation
          def createInformationAnnotation(node: ASTNode, message: String): Annotation = FakeAnnotation
          def createInformationAnnotation(elt: PsiElement, message: String): Annotation = FakeAnnotation
          def createWarningAnnotation(range: TextRange, message: String): Annotation = FakeAnnotation
          def createWarningAnnotation(node: ASTNode, message: String): Annotation = FakeAnnotation
          def createWarningAnnotation(elt: PsiElement, message: String): Annotation = FakeAnnotation
          def createErrorAnnotation(range: TextRange, message: String): Annotation = {
            holder.registerProblem(element, "Error detected", ProblemHighlightType.ERROR)
            FakeAnnotation
          }
          def createErrorAnnotation(node: ASTNode, message: String): Annotation = {
            holder.registerProblem(element, "Error detected", ProblemHighlightType.ERROR)
            FakeAnnotation
          }
          def createErrorAnnotation(elt: PsiElement, message: String): Annotation = {
            holder.registerProblem(element, "Error detected", ProblemHighlightType.ERROR)
            FakeAnnotation
          }

          def getCurrentAnnotationSession: AnnotationSession = {
            new AnnotationSession(element.getContainingFile)
          }
        }
        annotator.annotate(element, annotationHolder)
      }
    }
  }
}