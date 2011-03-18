package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.openapi.editor.{RangeMarker, Editor}
import dependency._
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
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Pavel Fatin
 */

class ScalaCopyPastePostProcessor extends CopyPastePostProcessor[DependencyData] {
  def collectTransferableData(file: PsiFile, editor: Editor,
                              startOffsets: Array[Int], endOffsets: Array[Int]): DependencyData = {
    if(!file.isInstanceOf[ScalaFile]) return null

    val elements = 
      for((startOffset, endOffset) <- startOffsets.zip(endOffsets);
          element <- CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset))
      yield (element, startOffset)
     
    val referenceDependencies =
      for((element, startOffset) <- elements;
          reference <- element.asOptionOf(classOf[ScReferenceElement]) if reference.qualifier.isEmpty;
          target <- reference.resolve().toOption if target.getContainingFile != file;
          dependency <- dependencyFor(element, startOffset, target)) yield dependency

    val conversionDependencies =
      for((element, startOffset) <- elements;
        exp <- element.asOptionOf(classOf[ScExpression]);
        tr = exp.getTypeAfterImplicitConversion() if tr.importsUsed.nonEmpty;
        named <- tr.implicitFunction;
        Both(member, ContainingClass(obj: ScObject)) <- named.asOptionOf(classOf[ScMember]))
      yield ImplicitConversionDependency(element, startOffset, obj.getQualifiedName, member.getName)

    new DependencyData(referenceDependencies ++ conversionDependencies)
  }

  private def dependencyFor(element: PsiElement, startOffset: Int, target: PsiElement) = {
    Some(target) collect {
      case e: PsiClass =>
        TypeDependency(element, startOffset, e.getQualifiedName)
      case e: PsiPackage =>
        PackageDependency(element, startOffset, e.getQualifiedName)
      case Both(_: ScPrimaryConstructor, Parent(parent: PsiClass)) =>
        PrimaryConstructorDependency(element, startOffset, parent.getQualifiedName)
      case Both(member: ScMember, ContainingClass(obj: ScObject)) =>
        MemberDependency(element, startOffset, obj.getQualifiedName, member.getName)
      case Both(member: PsiMember, ContainingClass(aClass: PsiClass)) =>
        MemberDependency(element, startOffset, aClass.getQualifiedName, member.getName)
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
      // add imports for reference dependencies
      for((dependency, Some(ref)) <- refs if ref.resolve() == null;
          holder = ScalaImportClassFix.getImportHolder(ref, file.getProject)) {
        dependency match {
          case TypeDependency(_, _, ClassFromName(aClass)) =>
            holder.addImportForClass(aClass, ref)
          case PackageDependency(_, _, packageName) =>
            holder.addImportForPath(packageName, ref)
          case PrimaryConstructorDependency(_, _, ClassFromName(aClass)) =>
            holder.addImportForClass(aClass, ref)
          case MemberDependency(_, _, className @ ClassFromName(_), memberName) =>
            holder.addImportForPath("%s.%s".format(className, "_"), ref)
          case _ =>
        }
      }

      // add imports for implicit conversion dependencies
      for(dependency <- value.dependencies) {
        dependency match {
          case ImplicitConversionDependency(_, _, className @ ClassFromName(_), memberName) =>
            val holder = file.asInstanceOf[ScalaFile]
            holder.addImportForPath("%s.%s".format(className, "_"))
          case _ =>
        }
      }
    }
  }

  private def findReferencesIn(file: PsiFile, bounds: RangeMarker, dependencies: Seq[Dependency]) = {
    for(dependency <- dependencies;
        range = new TextRange(dependency.startOffset, dependency.endOffset).shiftRight(bounds.getStartOffset);
        ref <- Option(file.findElementAt(range.getStartOffset))) yield
      ref match {
        case Parent(expr: ScReferenceElement) if expr.getTextRange == range => (dependency, Some(expr))
        case _ => (dependency, None)
      }
  }
}
