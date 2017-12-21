package org.jetbrains.plugins.scala.codeInspection.prefixMutableCollections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement.withQualifier
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createExpressionWithContextFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.psi.{ElementScope, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult.withActual

/**
  * @author Alefas
  * @since 26.05.12
  */
class ReferenceMustBePrefixedInspection extends AbstractInspection(ReferenceMustBePrefixedInspection.ID, ReferenceMustBePrefixedInspection.DESCRIPTION) {

  import ReferenceMustBePrefixedInspection._

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case reference: ScReferenceElement if reference.qualifier.isEmpty && !reference.getParent.isInstanceOf[ScImportSelector] =>
      reference.bind().collect {
        case result@withActual(clazz: PsiClass) if result.nameShadow.isEmpty && isValid(clazz, reference) => clazz
      }.flatMap(validFqnSegments)
        .map(new AddPrefixQuickFix(reference, _))
        .foreach(registerProblem(reference, _))
  }

  private def registerProblem(reference: ScReferenceElement, quickFix: AddPrefixQuickFix)
                             (implicit holder: ProblemsHolder): Unit = {
    holder.registerProblem(reference, getDisplayName, quickFix)
  }
}

object ReferenceMustBePrefixedInspection {

  private val ID = "ReferenceMustBePrefixed"
  val DESCRIPTION = "Reference must be prefixed"

  private def isValid(clazz: PsiClass, reference: ScReferenceElement): Boolean =
    ScalaPsiUtil.hasStablePath(clazz) && !PsiTreeUtil.isAncestor(clazz.containingClass, reference, true)

  private def validFqnSegments(clazz: PsiClass)
                              (implicit holder: ProblemsHolder): Option[Array[String]] = {
    val settings = ScalaCodeStyleSettings.getInstance(holder.getProject)
    val qualifiedName = clazz.qualifiedName

    qualifiedName.split('.') match {
      case fqn if fqn.length >= 2 && settings.hasImportWithPrefix(qualifiedName) => Some(fqn)
      case _ => None
    }
  }

  private class AddPrefixQuickFix(reference: ScReferenceElement, segments: Seq[String])
    extends AbstractFixOnPsiElement(AddPrefixQuickFix.HINT, reference) {

    import AddPrefixQuickFix._

    override protected def doApplyFix(reference: ScReferenceElement)
                                     (implicit project: Project): Unit = {
      def replaceReference = {
        val replacementText = segments.takeRight(2).mkString(".")

        val maybeReplacement = reference match {
          case _: ScStableCodeReferenceElement => Some(createReferenceFromText(replacementText))
          case ref: ScReferenceExpression => Some(createExpressionWithContextFromText(replacementText, ref.getContext, ref))
          case _ => None
        }

        maybeReplacement.map(reference.replace).collect {
          case withQualifier(element: ScReferenceElement) => element
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

    val HINT = "Add prefix to reference"

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
