package org.jetbrains.plugins.scala.annotator.element

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScNamingPattern, ScPattern, ScPatternArgumentList, ScSeqWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

object ScPatternTypeUnawareAnnotator extends ElementAnnotator[ScPattern] {

  override def annotate(pattern: ScPattern, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    pattern match {
      case namedPattern: ScNamingPattern => processNamedPattern(namedPattern)
      //case seqWildcard: ScSeqWildcard    => processSeqWildcardPattern(seqWildcard)
      case _                             =>
    }

  /*private def processSeqWildcardPattern(seqWildcard: ScSeqWildcard)
                                        (implicit holder: ScalaAnnotationHolder): Unit =
    if (seqWildcard.getContext.is[ScPatternArgumentList] && seqWildcard.isInScala3Module) {
      val annotation = holder.createWarningAnnotation(seqWildcard, ScalaBundle.message("vararg.short.pattern.with.at.deprecated.since.scala3"))
      annotation.setHighlightType(compatHighlightType(seqWildcard))
      annotation.registerFix(new ReplaceWithScala3WildcardVarargFix(seqWildcard))
    }*/

  private def compatHighlightType(element: PsiElement): ProblemHighlightType =
    if (element.isCompilerStrictMode) {
      ProblemHighlightType.GENERIC_ERROR
    } else {
      ProblemHighlightType.WEAK_WARNING // TODO: in case of warning be able to mute it?
    }

  private def processNamedPattern(pattern: ScNamingPattern)
                                  (implicit holder: ScalaAnnotationHolder): Unit = {
    bindingElement(pattern) match {
      case Some(binder) =>
        binder.elementType match {
          case ScalaTokenTypes.tCOLON if !pattern.isInScala3Module =>
            val annotation = holder.createWarningAnnotation(binder, ScalaBundle.message("vararg.pattern.with.colon.requires.scala3"))
            annotation.setHighlightType(ProblemHighlightType.GENERIC_ERROR)
            annotation.registerFix(new ReplaceNamingPatternBindingElementInScala2Fix(binder))
          case ScalaTokenTypes.tAT if pattern.isInScala3Module =>
            val annotation = holder.createWarningAnnotation(binder, ScalaBundle.message("vararg.pattern.with.at.deprecated.since.scala3"))
            annotation.setHighlightType(compatHighlightType(pattern))
            annotation.registerFix(new ReplaceNamingPatternBindingElementInScala3Fix(pattern))
          case _ =>
        }
      case _ =>
    }
  }

  /** @return psi element for `@` or `:` */
  private def bindingElement(pattern: ScNamingPattern): Option[PsiElement] =
    pattern.named match {
      case seqWildcard: ScSeqWildcardPattern => Option(seqWildcard.getPrevSiblingNotWhitespace)
      case _                          => None
    }

  private class ReplaceNamingPatternBindingElementInScala2Fix(binder: PsiElement) extends IntentionAction with DumbAware {
    override def getText: String = ScalaBundle.message("replace.with.type", "@")

    override def getFamilyName: String = ScalaBundle.message("family.name.replace.type.with.type.in.vararg.pattern")

    override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override def startInWriteAction(): Boolean = true

    override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
      if (!binder.isValid) return
      val pattern = ScalaPsiElementFactory.createPatternFromText(s"List(_ @ _*)")(project)
      val newBinder = pattern.elements.find(_.elementType == ScalaTokenTypes.tAT)
      newBinder.foreach(binder.replace)
    }
  }

  private class ReplaceNamingPatternBindingElementInScala3Fix(namingPattern: ScNamingPattern) extends IntentionAction with DumbAware {
    override def getText: String = ScalaBundle.message("replace.with.type", namingPattern.name + "*")

    override def getFamilyName: String = ScalaBundle.message("family.name.replace.with.scala3.vararg.pattern")

    override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override def startInWriteAction(): Boolean = true

    override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
      if (!namingPattern.isValid) return
      val pattern = ScalaPsiElementFactory.createPatternFromText(s"List(${namingPattern.name}*)")(project)
      val newPattern = pattern.elements.find(_.elementType == ScalaElementType.SEQ_WILDCARD_PATTERN)
      newPattern.foreach(namingPattern.replace)
    }
  }

  /*private class ReplaceWithScala3WildcardVarargFix(varargShort: ScSeqWildcard) extends IntentionAction with DumbAware {
    override def getText: String = ScalaBundle.message("replace.with.scala3.wildcard.varargs")

    override def getFamilyName: String = ScalaBundle.message("family.name.replace.old.varags.with.scala3.varargs.pattern")

    override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    override def startInWriteAction(): Boolean = true

    override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
      if (!varargShort.isValid) return
      val pattern = ScalaPsiElementFactory.createPatternFromText(s"List(_: _*)")(project)
      val varargsFull = pattern.elements.findByType[ScNamingPattern]
      varargsFull.foreach(varargShort.replace)
    }
  }*/
}
