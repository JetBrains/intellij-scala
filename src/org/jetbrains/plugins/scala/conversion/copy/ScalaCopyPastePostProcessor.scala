package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.openapi.editor.{RangeMarker, Editor}
import java.lang.Boolean
import java.awt.datatransfer.Transferable
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil
import collection.JavaConversions._
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.Ref
import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportClassFix
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.codeInsight.CodeInsightSettings
import org.jetbrains.plugins.scala.lang.dependency.Dependency
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.settings._
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/**
 * Pavel Fatin
 */

class ScalaCopyPastePostProcessor extends CopyPastePostProcessor[Associations] {
  def collectTransferableData(file: PsiFile, editor: Editor,
                              startOffsets: Array[Int], endOffsets: Array[Int]): Associations = {
    if (DumbService.getInstance(file.getProject).isDumb) return null

    if(!file.isInstanceOf[ScalaFile]) return null

    val associations = for((startOffset, endOffset) <- startOffsets.zip(endOffsets);
                           element <- CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset);
                           reference <- element.asOptionOf[ScReferenceElement];
                           dependency <- Dependency.dependencyFor(reference) if dependency.isExternal;
                           range = dependency.source.getTextRange.shiftRight(-startOffset))
    yield Association(dependency.kind, range, dependency.path)

    new Associations(associations)
  }

  def extractTransferableData(content: Transferable) = {
    content.isDataFlavorSupported(Associations.Flavor)
            .ifTrue(content.getTransferData(Associations.Flavor).asInstanceOf[Associations])
            .orNull
  }

  def processTransferableData(project: Project, editor: Editor, bounds: RangeMarker,
                              caretColumn: Int, indented: Ref[Boolean], value: Associations) {
    if (DumbService.getInstance(project).isDumb) return

    if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.NO) return

    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)

    if (!file.isInstanceOf[ScalaFile]) return

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val bindings = for(association <- value.associations;
                       element <- elementFor(association, file, bounds)
                       if (!association.kind.isSatisfiedIn(element)))
    yield Binding(element, association.path.asString(ScalaProjectSettings.getInstance(project).
              isImportMembersUsingUnderScore))

    val bindingsToRestore = bindings.distinctBy(_.path)

    if (bindingsToRestore.isEmpty) return

    val bs = if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.ASK) {
      val dialog = new RestoreReferencesDialog(project, bindingsToRestore.map(_.path.toOption.getOrElse("")).sorted.toArray)
      dialog.show()
      val selectedPahts = dialog.getSelectedElements
      if (dialog.getExitCode == DialogWrapper.OK_EXIT_CODE)
        bindingsToRestore.filter(it => selectedPahts.contains(it.path))
      else
        Seq.empty
    } else {
      bindingsToRestore
    }

    inWriteAction {
      for(Binding(ref, path) <- bs;
          holder = ScalaImportClassFix.getImportHolder(ref, file.getProject))
        holder.addImportForPath(path, ref)
    }
  }

  private def elementFor(dependency: Association, file: PsiFile, bounds: RangeMarker): Option[PsiElement] = {
    val range = dependency.range.shiftRight(bounds.getStartOffset)

    for(ref <- Option(file.findElementAt(range.getStartOffset));
        parent <- ref.parent if parent.getTextRange == range) yield parent
  }

  case class Binding(element: PsiElement, path: String)
}