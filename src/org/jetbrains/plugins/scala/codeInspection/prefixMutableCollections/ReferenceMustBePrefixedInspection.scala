package org.jetbrains.plugins.scala.codeInspection.prefixMutableCollections

import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.toPsiClassExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.codeInspection.{AbstractFix, AbstractInspection}
import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, ProblemsHolder}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScStableCodeReferenceElement, ScReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector

/**
 * @author Alefas
 * @since 26.05.12
 */

class ReferenceMustBePrefixedInspection extends AbstractInspection("ReferenceMustBePrefixed", "Reference must be prefixed") {
  def actionFor(holder: ProblemsHolder) = {
    case ref: ScReferenceElement if ref.qualifier == None && !ref.getParent.isInstanceOf[ScImportSelector] =>
      ref.bind() match {
        case Some(r: ScalaResolveResult) if r.nameShadow.isEmpty =>
          r.getActualElement match {
            case clazz: PsiClass =>
              val qualName = clazz.qualifiedName
              if (ScalaProjectSettings.getInstance(holder.getProject).hasImportWithPrefix(qualName)) {
                holder.registerProblem(ref, getDisplayName, new AddPrefixFix(ref, clazz))
              }
            case _ =>
          }
        case _ =>
      }
  }
}

class AddPrefixFix(ref: ScReferenceElement, clazz: PsiClass) extends AbstractFix("Add prefix to reference", ref) {
  def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!ref.isValid || !clazz.isValid) return
    val parts = clazz.qualifiedName.split('.')
    if (parts.length < 2) return
    val newRefText = parts.takeRight(2).mkString(".")
    ref match {
      case ref: ScStableCodeReferenceElement =>
        val newRef = ScalaPsiElementFactory.createReferenceFromText(newRefText, ref.getManager)
        ref.replace(newRef)
        newRef.bindToElement(clazz)
      case ref: ScReferenceExpression =>
        val newRef = ScalaPsiElementFactory.createExpressionFromText(newRefText, ref.getManager).asInstanceOf[ScReferenceExpression]
        ref.replace(newRef)
        newRef.bindToElement(clazz)
      case _ => return
    }
  }
}
