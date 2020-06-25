package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, _}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScNamingPattern, ScReferencePattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createWildcardNode, createWildcardPattern}

class DeleteUnusedElementFix(e: ScNamedElement) extends LocalQuickFixAndIntentionActionOnPsiElement(e) {
  override def getText: String = ScalaInspectionBundle.message("remove.unused.element")

  override def getFamilyName: String = getText

  override def startInWriteAction(): Boolean = false

  override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit = {
    if (FileModificationService.getInstance.prepareFileForWrite(startElement.getContainingFile)) {
      startElement match {
        case p: ScParameter if !p.owner.is[ScFunctionExpr] => removeParameter(p, project)
        case _ =>
          removeInWriteAction(startElement, project)
      }
    }
  }

  private def removeInWriteAction(startElement: PsiElement, project: Project): Unit = inWriteAction {
    def wildcard = createWildcardNode(project).getPsi
    startElement match {
      case ref: ScReferencePattern => ref.getContext match {
        case pList: ScPatternList if pList.patterns == Seq(ref) =>
          val context: PsiElement = pList.getContext
          context.getContext.deleteChildRange(context, context)
        case pList: ScPatternList if pList.simplePatterns && pList.patterns.startsWith(Seq(ref)) =>
          val end = ref.nextSiblings.find(_.getNode.getElementType == ScalaTokenTypes.tCOMMA).get.getNextSiblingNotWhitespace.getPrevSibling
          pList.deleteChildRange(ref, end)
        case pList: ScPatternList if pList.simplePatterns =>
          val start = ref.prevSiblings.find(_.getNode.getElementType == ScalaTokenTypes.tCOMMA).get.getPrevSiblingNotWhitespace.getNextSibling
          pList.deleteChildRange(start, ref)
        case _ =>
          // val (a, b) = t
          // val (_, b) = t
          ref.replace(createWildcardPattern(project))
      }
      case typed: ScTypedPattern => typed.nameId.replace(wildcard)
      case p: ScParameter => p.nameId.replace(wildcard)
      case naming: ScNamingPattern => naming.replace(naming.named)
      case _ => startElement.delete()
    }
  }

  private def removeParameter(p: ScParameter, project: Project): Unit = {
    val processor = SafeDeleteProcessor.createInstance(project, null, Array(p), true, true)
    processor.run()
  }
}
