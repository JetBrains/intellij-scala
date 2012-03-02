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
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScConstructorPattern
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import com.intellij.codeInsight.CodeInsightSettings
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScPostfixExpr, ScExpression}
import com.intellij.openapi.ui.DialogWrapper
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

/**
 * Pavel Fatin
 */

class ScalaCopyPastePostProcessor extends CopyPastePostProcessor[DependencyData] {
  def collectTransferableData(file: PsiFile, editor: Editor,
                              startOffsets: Array[Int], endOffsets: Array[Int]): DependencyData = {
    if (DumbService.getInstance(file.getProject).isDumb) return null

    if(!file.isInstanceOf[ScalaFile]) return null

    val elements = 
      for((startOffset, endOffset) <- startOffsets.zip(endOffsets);
          element <- CollectHighlightsUtil.getElementsInRange(file, startOffset, endOffset))
      yield (element, startOffset)
     
    val referenceDependencies =
      for((element, startOffset) <- elements;
          reference <- element.asOptionOf[ScReferenceElement] if isPrimary(reference);
          target <- reference.resolve().toOption if target.getContainingFile != file;
          dependency <- dependencyFor(reference, startOffset, target)) yield dependency

    val conversionDependencies =
      for((element, startOffset) <- elements;
        exp <- element.asOptionOf[ScExpression];
        tr = exp.getTypeAfterImplicitConversion();
        named <- tr.implicitFunction;
        Both(member, ContainingClass(obj: ScObject)) <- named.asOptionOf[ScMember])
      yield ImplicitConversionDependency(element, startOffset, obj.qualifiedName, named.name)

    new DependencyData(referenceDependencies ++ conversionDependencies)
  }

  private def isPrimary(ref: ScReferenceElement) = ref match {
    case it @ Parent(postfix: ScPostfixExpr) => it == postfix.operand
    case it @ Parent(infix: ScInfixExpr) => it == infix.lOp
    case it => it.qualifier.isEmpty
  }

  private def dependencyFor(element: ScReferenceElement, startOffset: Int, target: PsiElement) = {
    element match {
      case Parent(_: ScConstructorPattern) =>
        target match {
          case ContainingClass(aClass) =>
            Some(PatternDependency(element, startOffset, aClass.qualifiedName))
          case aClass: ScSyntheticClass =>
            Some(PatternDependency(element, startOffset, aClass.qualifiedName))
          case _ => None
        }
      case _ =>
        Some(target) collect pf( // workaround for scalac pattern matcher bug. See SCL-3150
        {case e: PsiClass => TypeDependency(element, startOffset, e.qualifiedName)},
        {case e: PsiPackage => PackageDependency(element, startOffset, e.getQualifiedName)},
        {case Both(_: ScPrimaryConstructor, Parent(parent: PsiClass)) =>
          PrimaryConstructorDependency(element, startOffset, parent.qualifiedName)},
        {case Both(member: ScMember, ContainingClass(obj: ScObject)) =>
          MemberDependency(element, startOffset, obj.qualifiedName, member match {
            case named: ScNamedElement => named.name
            case _ => member.getName
          })},
        {case Both(method: PsiMethod, ContainingClass(aClass: PsiClass)) if method.isConstructor =>
          TypeDependency(element, startOffset, aClass.qualifiedName)},
        {case Both(member: PsiMember, ContainingClass(aClass: PsiClass)) =>
          MemberDependency(element, startOffset, aClass.qualifiedName, member match {
            case named: ScNamedElement => named.name
            case _ => member.getName
          })}
        )
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

    val settings = ScalaCodeStyleSettings.getInstance(project)

    if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.NO) return

    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)

    if (!file.isInstanceOf[ScalaFile]) return

    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val bindings = value.dependencies
            .map(it => Binding(it, it.path(settings.IMPORTS_MEMBERS_USING_UNDERSCORE), elementFor(it, file, bounds)))
            .distinctBy(_.path)

    val referenceBindings = bindings.filter {
      case Binding(_: ImplicitConversionDependency, _, _) => false
      case Binding(_, _, Some(ref: ScReferenceElement)) => ref.resolve() == null
      case _ => false
    }

    val conversionBindings = bindings.filter {
      case Binding(_: ImplicitConversionDependency, _, Some(exp: ScExpression)) =>
          exp.getTypeAfterImplicitConversion().implicitFunction.isEmpty
      case _ => false
    }

    val bindingsToRestore = (referenceBindings ++ conversionBindings).distinct

    if (bindingsToRestore.isEmpty) return

    val bs = if (CodeInsightSettings.getInstance().ADD_IMPORTS_ON_PASTE == CodeInsightSettings.ASK) {
      val dialog = new RestoreReferencesDialog(project, bindingsToRestore.map(_.path).sorted.toArray)
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
      for(Binding(_, path, Some(ref)) <- bs; holder = ScalaImportClassFix.getImportHolder(ref, file.getProject))
        holder.addImportForPath(path, ref)
    }
  }

  private def elementFor(dependency: Dependency, file: PsiFile, bounds: RangeMarker): Option[PsiElement] = {
    val range = new TextRange(dependency.startOffset, dependency.endOffset).shiftRight(bounds.getStartOffset)

    for(ref <- Option(file.findElementAt(range.getStartOffset));
        parent <- ref.parent if parent.getTextRange == range) yield parent
  }

  case class Binding(dependency: Dependency, path: String, element: Option[PsiElement])
}