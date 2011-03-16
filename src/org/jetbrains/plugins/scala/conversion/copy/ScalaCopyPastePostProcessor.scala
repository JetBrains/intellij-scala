package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.openapi.editor.{RangeMarker, Editor}
import java.lang.Boolean
import java.awt.datatransfer.Transferable
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.ui.Messages
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil
import collection.JavaConversions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.extensions._
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.{TextRange, Ref}
import com.intellij.psi._
import org.jetbrains.plugins.scala.conversion.copy.ScalaData.ReferenceData

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
        case t: PsiClass => refs ::= createReferenceData(element, startOffset, t.getQualifiedName)
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
    if(content.isDataFlavorSupported(ReferenceData.getDataFlavor))
      content.getTransferData(ReferenceData.getDataFlavor).asInstanceOf[ScalaData]
    else
      null
  }

  def processTransferableData(project: Project, editor: Editor, bounds: RangeMarker,
                              caretColumn: Int, indented: Ref[Boolean], value: ScalaData) {
    if (DumbService.getInstance(project).isDumb) return

    val document = editor.getDocument
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document)

    if (!file.isInstanceOf[ScalaFile]) return

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val data = value.getData
    val refs = findReferencesIn(file, bounds, data)

    refs.foreach { it =>

    }
  }

  private def findReferencesIn(file: PsiFile, bounds: RangeMarker,
                               datas: Array[ReferenceData]): Seq[ScReferenceElement] = {
    val manager = file.getManager
    val facade = JavaPsiFacade.getInstance(manager.getProject)

    var refs = List[ScReferenceElement]()

    for(data <- datas; refClass <- Option(facade.findClass(data.qClassName, file.getResolveScope))) {
      val range = new TextRange(data.startOffset, data.endOffset).shiftRight(bounds.getStartOffset)
      val ref = file.findElementAt(range.getStartOffset)
      ref match {
        case Parent(expr: ScReferenceElement) if expr.getTextRange == range => refs ::= expr
        case _ =>
      }
    }

    refs.reverse
  }
}