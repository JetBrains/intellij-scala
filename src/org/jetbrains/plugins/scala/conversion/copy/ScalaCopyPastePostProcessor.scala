package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.openapi.editor.{RangeMarker, Editor}
import java.lang.Boolean
import java.awt.datatransfer.Transferable
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil
import collection.JavaConversions._
import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.{TextRange, Ref}
import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportClassFix
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScPrimaryConstructor, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScMember}
import org.jetbrains.plugins.scala.extensions._

/**
 * Pavel Fatin
 */

class ScalaCopyPastePostProcessor extends CopyPastePostProcessor[DependencyData] {
  def collectTransferableData(file: PsiFile, editor: Editor,
                              startOffsets: Array[Int], endOffsets: Array[Int]): DependencyData = {
    if(!file.isInstanceOf[ScalaFile]) return null

    val dependencies =
      for((startOffset, endOffset) <- startOffsets.zip(endOffsets);
          element <- CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset);
          reference <- element.asOptionOf(classOf[ScReferenceElement]) if reference.qualifier.isEmpty;
          target <- reference.resolve().toOption if target.getContainingFile != file;
          dependency <- dependencyFor(element, startOffset, target)) yield dependency

    new DependencyData(dependencies)
  }

  private def dependencyFor(element: PsiElement, startOffset: Int, target: PsiElement) = {
    Some(target) collect {
      case e: PsiClass =>
        TypeDependency(element, startOffset, e.getQualifiedName)
      case Both(_: ScPrimaryConstructor, Parent(parent: PsiClass)) =>
        PrimaryConstructorDependency(element, startOffset, parent.getQualifiedName)
      case Both(m: ScMember, ContainingClass(obj: ScObject)) =>
        MemberDependency(element, startOffset, obj.getQualifiedName, m.getName)
      case Both(m: PsiMember, ContainingClass(aClass: PsiClass)) =>
        MemberDependency(element, startOffset, aClass.getQualifiedName, m.getName)
    }
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

    object ClassFromName {
      def unapply(name: String) =
        Option(JavaPsiFacade.getInstance(file.getProject).findClass(name, file.getResolveScope))
    }

    inWriteAction {
      for((dependency, Some(ref)) <- refs if ref.resolve() == null;
          holder = ScalaImportClassFix.getImportHolder(ref, file.getProject)) {
        dependency match {
          case TypeDependency(_, _, ClassFromName(aClass)) =>
            holder.addImportForClass(aClass, ref)
          case PrimaryConstructorDependency(_, _, ClassFromName(aClass)) =>
            holder.addImportForClass(aClass, ref)
          case MemberDependency(_, _, className @ ClassFromName(_), memberName) =>
            holder.addImportForPath("%s.%s".format(className, "_"), ref)
          case _ =>
        }
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
