package org.jetbrains.plugins.scala.codeInspection.prefixMutableCollections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.{JavaPsiFacade, PsiClass}
import org.jetbrains.plugins.scala.codeInspection.prefixMutableCollections.ReferenceMustBePrefixedInspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnTwoPsiElements, AbstractInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * @author Alefas
 * @since 26.05.12
 */

class ReferenceMustBePrefixedInspection extends AbstractInspection(id, displayName) {
  def actionFor(holder: ProblemsHolder) = {
    case ref: ScReferenceElement if ref.qualifier == None && !ref.getParent.isInstanceOf[ScImportSelector] =>
      ref.bind() match {
        case Some(r: ScalaResolveResult) if r.nameShadow.isEmpty =>
          r.getActualElement match {
            case clazz: PsiClass =>
              val qualName = clazz.qualifiedName
              if (ScalaCodeStyleSettings.getInstance(holder.getProject).hasImportWithPrefix(qualName)) {
                holder.registerProblem(ref, getDisplayName, new AddPrefixFix(ref, clazz))
              }
            case _ =>
          }
        case _ =>
      }
  }
}

object ReferenceMustBePrefixedInspection {
  val id = "ReferenceMustBePrefixed"
  val displayName = "Reference must be prefixed"
}

class AddPrefixFix(ref: ScReferenceElement, clazz: PsiClass)
        extends AbstractFixOnTwoPsiElements(AddPrefixFix.hint, ref, clazz) {
  def doApplyFix(project: Project) {
    val refElem = getFirstElement
    val cl = getSecondElement
    if (!refElem.isValid || !cl.isValid) return
    val parts = cl.qualifiedName.split('.')
    val packageName = parts.dropRight(1).mkString(".")
    val pckg = JavaPsiFacade.getInstance(cl.getProject).findPackage(packageName)
    if (parts.length < 2) return
    val newRefText = parts.takeRight(2).mkString(".")
    refElem match {
      case stRef: ScStableCodeReferenceElement =>
        stRef.replace(ScalaPsiElementFactory.createReferenceFromText(newRefText, stRef.getManager)) match {
          case r: ScStableCodeReferenceElement => r.qualifier.foreach(_.bindToPackage(pckg, addImport = true))
          case _ =>
        }
      case ref: ScReferenceExpression =>
        ref.replace(ScalaPsiElementFactory.createExpressionWithContextFromText(newRefText, ref.getContext, ref)) match {
          case ScReferenceExpression.withQualifier(q: ScReferenceExpression) => q.bindToPackage(pckg, addImport = true)
          case _ =>
        }
      case _ =>
    }
  }
}

object AddPrefixFix {
  val hint = "Add prefix to reference"
}
