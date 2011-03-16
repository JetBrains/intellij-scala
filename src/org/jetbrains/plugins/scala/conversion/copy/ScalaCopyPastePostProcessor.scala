package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.openapi.editor.{RangeMarker, Editor}
import java.lang.Boolean
import java.awt.datatransfer.Transferable
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil
import collection.JavaConversions._
import org.jetbrains.plugins.scala.extensions._
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.{TextRange, Ref}
import com.intellij.psi._
import org.jetbrains.plugins.scala.conversion.copy.ScalaData.ReferenceData
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportClassFix
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReferenceElement}

/**
 * Pavel Fatin
 */

class ScalaCopyPastePostProcessor extends CopyPastePostProcessor[ScalaData] {
  def collectTransferableData(file: PsiFile, editor: Editor,
                              startOffsets: Array[Int], endOffsets: Array[Int]): ScalaData = {
    if(!file.isInstanceOf[ScalaFile]) return null

    var refs = List[ReferenceData]()

    for((startOffset, endOffset) <- startOffsets.zip(endOffsets);
        element <- CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset);
        reference <- element.asOptionOf(classOf[ScReferenceElement]) if reference.qualifier.isEmpty;
        target <- reference.resolve().toOption if target.getContainingFile != file) {
      target match {
        case e: PsiClass =>
          refs ::= createReferenceData(element, startOffset, e.getQualifiedName)
        case e: ScPrimaryConstructor =>
          e.getParent match {
            case e: PsiClass =>
              refs ::= createReferenceData(element, startOffset, e.getQualifiedName)
            case _ =>
          }
        case _ =>
      }
    }

    new ScalaData(refs.reverse.toArray)
  }

  private def createReferenceData(element: PsiElement, startOffset: Int,
                                  className: String, memberName: String = null) = {
    val range = element.getTextRange
    new ReferenceData(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className, memberName)
  }

  def extractTransferableData(content: Transferable) = {
    content.isDataFlavorSupported(ReferenceData.getDataFlavor)
            .ifTrue(content.getTransferData(ReferenceData.getDataFlavor).asInstanceOf[ScalaData])
            .orNull
  }

  def processTransferableData(project: Project, editor: Editor, bounds: RangeMarker,
                              caretColumn: Int, indented: Ref[Boolean], value: ScalaData) {
    if (DumbService.getInstance(project).isDumb) return

    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)

    if (!file.isInstanceOf[ScalaFile]) return

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val refs = findReferencesIn(file, bounds, value.getData)

    val facade = JavaPsiFacade.getInstance(file.getProject)

    for((data, Some(ref)) <- refs if ref.resolve() == null;
        refClass <- Option(facade.findClass(data.qClassName, file.getResolveScope));
        holder = ScalaImportClassFix.getImportHolder(ref, file.getProject)) {
      inWriteAction {
        holder.addImportForClass(refClass, ref)
      }
    }
  }

  private def findReferencesIn(file: PsiFile, bounds: RangeMarker, datas: Array[ReferenceData]) = {
    for(data <- datas;
        range = new TextRange(data.startOffset, data.endOffset).shiftRight(bounds.getStartOffset);
        ref = file.findElementAt(range.getStartOffset)) yield
      ref match {
        case Parent(expr: ScReferenceElement) if expr.getTextRange == range => (data, Some(expr))
        case _ => (data, None)
      }
  }
}