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
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportClassFix
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReferenceElement}

/**
 * Pavel Fatin
 */

class ScalaCopyPastePostProcessor extends CopyPastePostProcessor[DependencyData] {
  def collectTransferableData(file: PsiFile, editor: Editor,
                              startOffsets: Array[Int], endOffsets: Array[Int]): DependencyData = {
    if(!file.isInstanceOf[ScalaFile]) return null

    var dependencies = List[Dependency]()

    for((startOffset, endOffset) <- startOffsets.zip(endOffsets);
        element <- CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset);
        reference <- element.asOptionOf(classOf[ScReferenceElement]) if reference.qualifier.isEmpty;
        target <- reference.resolve().toOption if target.getContainingFile != file) {
      target match {
        case e: PsiClass =>
          dependencies ::= TypeDependency(element, startOffset, e.getQualifiedName)
        case e: ScPrimaryConstructor =>
          e.getParent match {
            case parent: PsiClass =>
              dependencies ::= PrimaryConstructorDependency(element, startOffset, parent.getQualifiedName)
            case _ =>
          }
        case _ =>
      }
    }

    new DependencyData(dependencies.reverse)
  }

  def extractTransferableData(content: Transferable) = {
    content.isDataFlavorSupported(DependencyData.Flavor)
            .ifTrue(content.getTransferData(DependencyData.Flavor).asInstanceOf[DependencyData])
            .orNull
  }

  def processTransferableData(project: Project, editor: Editor, bounds: RangeMarker,
                              caretColumn: Int, indented: Ref[Boolean], value: DependencyData) {
    if (DumbService.getInstance(project).isDumb) return

    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)

    if (!file.isInstanceOf[ScalaFile]) return

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val refs = findReferencesIn(file, bounds, value.dependencies)

    val facade = JavaPsiFacade.getInstance(file.getProject)

    for((data, Some(ref)) <- refs if ref.resolve() == null;
        refClass <- Option(facade.findClass(data.qClassName, file.getResolveScope));
        holder = ScalaImportClassFix.getImportHolder(ref, file.getProject)) {
      inWriteAction {
        holder.addImportForClass(refClass, ref)
      }
    }
  }

  private def findReferencesIn(file: PsiFile, bounds: RangeMarker, dependencies: Seq[Dependency]) = {
    for(dependency <- dependencies;
        range = new TextRange(dependency.startOffset, dependency.endOffset).shiftRight(bounds.getStartOffset);
        ref = file.findElementAt(range.getStartOffset)) yield
      ref match {
        case Parent(expr: ScReferenceElement) if expr.getTextRange == range => (dependency, Some(expr))
        case _ => (dependency, None)
      }
  }
}
