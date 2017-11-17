package org.jetbrains.plugins.scala.codeInspection.prefixMutableCollections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.codeInspection.prefixMutableCollections.ReferenceMustBePrefixedInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnTwoPsiElements, AbstractInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionWithContextFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author Alefas
  * @since 26.05.12
  */

class ReferenceMustBePrefixedInspection extends AbstractInspection(id, displayName) {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case ref: ScReferenceElement if ref.qualifier.isEmpty && !ref.getParent.isInstanceOf[ScImportSelector] =>
      ref.bind() match {
        case Some(r: ScalaResolveResult) if r.nameShadow.isEmpty =>
          r.getActualElement match {
            case clazz: PsiClass if ScalaPsiUtil.hasStablePath(clazz) =>
              val qualName = clazz.qualifiedName
              val settings = ScalaCodeStyleSettings.getInstance(holder.getProject)
              if (settings.hasImportWithPrefix(qualName) && !inContainingClass(ref, clazz)) {
                holder.registerProblem(ref, getDisplayName, new AddPrefixFix(ref, clazz))
              }
            case _ =>
          }
        case _ =>
      }
  }

  private def inContainingClass(ref: ScReferenceElement, c: PsiClass) = Option(c.containingClass).exists(_.isAncestorOf(ref))
}

object ReferenceMustBePrefixedInspection {
  val id = "ReferenceMustBePrefixed"
  val displayName = "Reference must be prefixed"
}

class AddPrefixFix(ref: ScReferenceElement, clazz: PsiClass)
  extends AbstractFixOnTwoPsiElements(AddPrefixFix.hint, ref, clazz) {

  override protected def doApplyFix(refElem: ScReferenceElement, cl: PsiClass)
                                   (implicit project: Project): Unit = {
    val parts = cl.qualifiedName.split('.')
    if (parts.length < 2) return

    val fqn = parts.dropRight(1).mkString(".")
    val element = findPackage(project, fqn) orElse findClass(ref, fqn) match {
      case Some(named: PsiNamedElement) => named
      case _ => return
    }

    val newRefText = parts.takeRight(2).mkString(".")
    refElem match {
      case stRef: ScStableCodeReferenceElement =>
        val replaced = stRef.replace(createReferenceFromText(newRefText))
        bindQualifier(replaced, element)
      case ref: ScReferenceExpression =>
        val replaced = ref.replace(createExpressionWithContextFromText(newRefText, ref.getContext, ref))
        bindQualifier(replaced, element)
      case _ =>
    }
  }

  private def findPackage(project: Project, fqn: String) = Option(JavaPsiFacade.getInstance(project).findPackage(fqn))
  private def findClass(ref: ScReferenceElement, fqn: String) = {
    val elemScope = ref.elementScope
    ScalaShortNamesCacheManager.getInstance(elemScope.project).getClassByFQName(fqn, elemScope.scope).toOption
  }
  private def bindQualifier(ref: PsiElement, target: PsiNamedElement): Unit = {
    val qual = ref match {
      case ScReferenceElement.withQualifier(q: ScReferenceElement) => q
      case _ => return
    }
    target match {
      case pckg: PsiPackage => qual.bindToPackage(pckg, addImport = true)
      case cl: PsiClass if ScalaPsiUtil.hasStablePath(cl) => qual.bindToElement(cl)
      case _ =>
    }
  }
}

object AddPrefixFix {
  val hint = "Add prefix to reference"
}
