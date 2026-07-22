package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.codeInsight.daemon.impl.{HighlightInfo, HighlightInfoType}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.autoImport.quickFix.{CBHSuggestionToImport, ImportCBHSuggestionFix}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Generates actionable quick fixes based on external compiler diagnostics.
 *
 * Analyzes compiler error messages and the local abstract syntax tree to provide
 * contextual resolutions for common issues, such as missing imports, missing parameters,
 * or unresolved references.
 */
private[highlighting] class ExternalHighlightingFixProvider(project: Project) {

  private val hasImportMessagesLineRegex = raw"(The following import|One of the following imports) might (make progress towards fixing|fix) the problem:".r
  private val importLineRegex = raw"\s{2}import (.+)".r

  def registerImportFixesFromMessage(message: String, highlightingRange: TextRange, file: PsiFile, highlightInfo: HighlightInfo.Builder): Unit = {
    val place = file.findElementAt(highlightingRange.getStartOffset)
    if (place == null) return

    message.linesIterator
      .dropWhile(hasImportMessagesLineRegex.matches)
      .drop(1)
      .collect { case importLineRegex(ref) => ref }
      .foreach { refText =>
        val importStmt = ScalaPsiElementFactory.createImportFromText(s"import $refText", place)
        val ref = importStmt.importExprs.head.reference.get
        val imports = ref.multiResolveScala(false)
          .map { result =>
            CBHSuggestionToImport(result.element, refText)
          }
          .toSeq
        //val fix = ScalaAddImportAction.cbhSuggested(editor, imports, place)
        val fix = ImportCBHSuggestionFix(imports, place)
        highlightInfo.registerFix(fix, null, null, highlightingRange, null)
      }
  }

  @RequiresReadLock
  def findUnresolvedReferenceFixes(file: PsiFile,
                                   range: TextRange,
                                   highlightInfoType: HighlightInfoType): Seq[IntentionAction] = {
    // e.g. on opening project we are in dump mode, and can't do resolve to search quickfixes
    if (file.getProject.isDisposed || highlightInfoType != HighlightInfoType.WRONG_REF || DumbService.isDumb(file.getProject))
      return Seq.empty

    val ref = PsiTreeUtil.findElementOfClassAtRange(file, range.getStartOffset, range.getEndOffset, classOf[ScReference])

    if (ref != null && ref.multiResolveScala(false).isEmpty)
      UnresolvedReferenceFixProvider.fixesFor(ref)
    else Seq.empty
  }
}