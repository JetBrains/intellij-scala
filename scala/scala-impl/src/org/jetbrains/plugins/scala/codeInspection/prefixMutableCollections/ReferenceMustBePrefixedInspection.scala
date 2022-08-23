package org.jetbrains.plugins.scala.codeInspection.prefixMutableCollections

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference.qualifier
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionWithContextFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult.withActual

class ReferenceMustBePrefixedInspection extends LocalInspectionTool {

  import ReferenceMustBePrefixedInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case reference: ScReference if reference.qualifier.isEmpty && !reference.getParent.isInstanceOf[ScImportSelector] =>
      reference.bind().collect {
        case result@withActual(clazz: PsiClass) if result.renamed.isEmpty && isValid(clazz, reference) => clazz
      }.flatMap(validFqnSegments(_, holder))
        .map(_.toIndexedSeq)
        .map(new AddPrefixQuickFix(reference, _))
        .foreach(registerProblem(reference, _, holder))
    case _ =>
  }

  private def registerProblem(reference: ScReference, quickFix: AddPrefixQuickFix, holder: ProblemsHolder): Unit = {
    holder.registerProblem(reference, getDisplayName, quickFix)
  }
}

object ReferenceMustBePrefixedInspection {
  private def isValid(clazz: PsiClass, reference: ScReference): Boolean =
    ScalaPsiUtil.hasStablePath(clazz) && !PsiTreeUtil.isAncestor(clazz.containingClass, reference, true)

  private def validFqnSegments(clazz: PsiClass, holder: ProblemsHolder): Option[Array[String]] = {
    val settings = ScalaCodeStyleSettings.getInstance(holder.getProject)
    val qualifiedName = clazz.qualifiedName

    qualifiedName.split('.') match {
      case fqn if fqn.length >= 2 && settings.hasImportWithPrefix(qualifiedName) => Some(fqn)
      case _ => None
    }
  }

  private class AddPrefixQuickFix(reference: ScReference, segments: Seq[String])
    extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("add.prefix.to.reference"), reference) {

    import AddPrefixQuickFix._

    override protected def doApplyFix(reference: ScReference)
                                     (implicit project: Project): Unit = {
      def replaceReference = {
        val replacementText = segments.takeRight(2).mkString(".")

        val maybeReplacement = reference match {
          case _: ScStableCodeReference => Some(createReferenceFromText(replacementText))
          case ref: ScReferenceExpression => Some(createExpressionWithContextFromText(replacementText, ref.getContext, ref))
          case _ => None
        }

        maybeReplacement.map(reference.replace).collect {
          case qualifier(element: ScReference) => element
        }
      }

      val fqn = segments.dropRight(1).mkString(".")
      findPackage(fqn) match {
        case Some(psiPackage) => replaceReference.foreach(_.bindToPackage(psiPackage, addImport = true))
        case _ => findClass(fqn)(reference.elementScope) match {
          case Some(clazz) => replaceReference.foreach(_.bindToElement(clazz))
          case _ =>
        }
      }
    }
  }

  object AddPrefixQuickFix {
    private def findPackage(fqn: String)
                           (implicit project: Project) = {
      val psiFacade = JavaPsiFacade.getInstance(project)
      Option(psiFacade.findPackage(fqn))
    }

    private def findClass(fqn: String)
                         (implicit elementScope: ElementScope) = {
      val ElementScope(project, scope) = elementScope
      val namesManager = ScalaShortNamesCacheManager.getInstance(project)
      Option(namesManager.getClassByFQName(fqn, scope))
    }
  }

}
