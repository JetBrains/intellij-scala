package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScNamingPattern, ScSeqWildcard}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

object ScNamingPatternAnnotator extends ElementAnnotator[ScNamingPattern] {

  override def annotate(pattern: ScNamingPattern, typeAware: Boolean = true)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    bindingElementType(pattern) match {
      case Some(binder) =>
        binder.elementType match {
          case ScalaTokenTypes.tCOLON if !pattern.isInScala3Module =>
            register(ScalaBundle.message("vararg.pattern.with.colon.requires.scala3"), binder, ScalaTokenTypes.tAT)
          case ScalaTokenTypes.tAT if pattern.isInScala3Module =>
            if (pattern.isScala2CompatibilityEnabled) {
              register(ScalaBundle.message("vararg.pattern.with.at.deprecated.in.scala3"), binder, ScalaTokenTypes.tCOLON, ProblemHighlightType.WEAK_WARNING)
            } else {
              register(ScalaBundle.message("vararg.pattern.with.at.disabled.in.scala3"), binder, ScalaTokenTypes.tCOLON)
            }
          case _ =>
        }
      case _ =>
    }

  private def register(message: String, element: PsiElement, replaceWithType: IElementType,
                       level: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR)
                      (implicit holder: ScalaAnnotationHolder): Unit = {
    val annotation = holder.createWarningAnnotation(element, message)
    annotation.setHighlightType(level)
    annotation.registerFix(new ReplaceFix(element, replaceWithType))
  }

  /** @return `@` or `:`*/
  private def bindingElementType(pattern: ScNamingPattern): Option[PsiElement] =
    pattern.named match {
      case seqWildcard: ScSeqWildcard => Option(seqWildcard.getPrevSiblingNotWhitespace)
      case _                          => None
    }

  private class ReplaceFix(element: PsiElement, replaceWithType: IElementType) extends IntentionAction with DumbAware {
    @inline private def text(typ: IElementType) = typ match {
      case ScalaTokenTypes.tCOLON => ":"
      case ScalaTokenTypes.tAT    => "@"
      case _                      => null
    }

    override def getText: String = s"Replace with '${text(replaceWithType)}'"

    override def getFamilyName: String = s"Replace '${text(element.elementType)}' with '${text(replaceWithType)}' in vararg pattern"

    override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override def startInWriteAction(): Boolean = true

    override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
      if (!element.isValid || project.isDisposed) return
      val pattern = ScalaPsiElementFactory.createPatternFromText(s"List(_ ${{text(replaceWithType)}} _*)")(project)
      val at = pattern.elements.find(_.elementType == replaceWithType)
      at.foreach(element.replace)
    }
  }
}
