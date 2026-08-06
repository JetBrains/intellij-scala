package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeInspection.{IntentionWrapper, ProblemsHolder}
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.search.{LocalSearchScope, PsiSearchHelper, TextOccurenceProcessor, UsageSearchContext}
import com.intellij.psi.{PsiAnnotationOwner, PsiElement, PsiElementVisitor, PsiReference}
import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaUnusedDeclarationInspection.hasUnusedAnnotation
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.incremental.Highlighting.ElementHighlightingExt
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.util.ScalaUsageNamesUtil

trait ScalaUnusedDeclarationIncrementalInspection { this: ScalaUnusedDeclarationInspection =>
  override def buildIncrementalHighlightingVisitor(holder: ProblemsHolder): PsiElementVisitor = {
    val helper = PsiSearchHelper.getInstance(holder.getProject)
    val context = (UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES).toShort
    PsiElementVisitorSimple(holder) {
      case e: ScNamedElement if e.nameId != null && e.nameId.isVisible(holder.getProject, holder.getFile) && !ScalaPsiUtil.isImplicit(e) && shouldProcessElement(e) =>
        val scope = helper.getUseScope(e)
        val ids = ScalaUsageNamesUtil.getStringsToSearch(e)
        val processor: TextOccurenceProcessor = e match {
          case cls: ScClass if ScalaPsiUtil.hasStablePath(cls) => // Can implement an extension point, include XML
            (e: PsiElement, _) => !(e.isInstanceOf[PsiReference] || e.getLanguage == XMLLanguage.INSTANCE)
          case _ =>
            (e: PsiElement, _) => !e.isInstanceOf[PsiReference]
        }
        val notFound = ids.stream.allMatch { id =>
          helper.processElementsWithWord(processor, scope, id, context, true)
        }
        if (notFound) {
          e match {
            case inNameContext(holder: PsiAnnotationOwner) if hasUnusedAnnotation(holder) =>
            case _ =>
              val info = unusedProblemInfoFor(e, isOnTheFly = true)
              holder.registerProblem(info.element, info.message, info.fixes: _*)
          }
        }
      case e: ScImportExpr if e.isVisible(holder.getProject, holder.getFile) && e.selectorSet.isEmpty && !e.hasWildcardSelector && !e.hasGivenSelector =>
        (e.getParent, e.reference) match {
          case (stmt: ScImportStmt, Some(reference)) if reference.refName.nonEmpty =>
            processImport(stmt, helper, reference.refName, context, reference, holder, e)
          case _ =>
        }
      case e: ScImportSelector if e.isVisible(holder.getProject, holder.getFile) && !e.isWildcardSelector && !e.isGivenSelector =>
        (e.parentImportExpression.getParent, e.reference, e.importedName) match {
          case (stmt: ScImportStmt, Some(reference), Some(id)) if id.nonEmpty =>
            processImport(stmt, helper, id, context, reference, holder, e)
          case _ =>
        }
      case _ =>
    }
  }

  private def processImport(stmt: ScImportStmt, helper: PsiSearchHelper, id: String, context: Short, reference: ScStableCodeReference, holder: ProblemsHolder, e: PsiElement): Unit = {
    val scope = new LocalSearchScope(stmt.getContext)
    val processor: TextOccurenceProcessor = (e: PsiElement, _) => !(e.isInstanceOf[PsiReference] && e != reference)
    if (helper.processElementsWithWord(processor, scope, id, context, true)) {
      val isImplicit = reference.multiResolveScala(incomplete = false).map(_.element).exists {
        case entity: ScNamedElement if ScalaPsiUtil.isImplicit(entity) => true
        case _ => false
      }
      if (!isImplicit && !ScalaCodeStyleSettings.getInstance(stmt.getProject).isAlwaysUsedImport(reference.qualName)) {
        holder.registerProblem(e, ScalaInspectionBundle.message("unused.import.statement"),
          new IntentionWrapper(new ScalaOptimizeImportsFix()), new IntentionWrapper(new MarkImportAsAlwaysUsed(reference.qualName)))
      }
    }
  }
}
