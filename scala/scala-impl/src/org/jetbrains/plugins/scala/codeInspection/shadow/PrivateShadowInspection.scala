package org.jetbrains.plugins.scala.codeInspection.shadow

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.ex.DisableInspectionToolAction
import com.intellij.codeInspection.ui.InspectionOptionsPanel
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.quickfix.RenameElementQuickfix
import org.jetbrains.plugins.scala.codeInspection.ui.CompilerInspectionOptions._
import org.jetbrains.plugins.scala.codeInspection.{AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps

import javax.swing.JComponent
import scala.beans.BooleanBeanProperty

final class PrivateShadowInspection extends AbstractRegisteredInspection {
  import PrivateShadowInspection._

  override protected def problemDescriptor(element:             PsiElement,
                                           maybeQuickFix:       Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType:       ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] =
    element match {
      case elem: ScNamedElement if
        isInspectionAllowed(elem, privateShadowCompilerOption, "-Xlint:private-shadow") &&
          isRegularClassParameter(elem) && isElementShadowing(elem) =>
        Some(createProblemDescriptor(elem, annotationDescription))
      case _ =>
        None
    }

  private def createProblemDescriptor(elem: ScNamedElement, @Nls description: String)
                                     (implicit manager: InspectionManager, isOnTheFly: Boolean): ProblemDescriptor = {
    val showAsError =
      privateShadowCompilerOption &&
        fatalWarningsCompilerOption &&
        (isCompilerOptionPresent(elem, "-Xfatal-warnings") || isCompilerOptionPresent(elem, "-Werror"))

    manager.createProblemDescriptor(
      elem.nameId,
      description,
      isOnTheFly,
      Array(new RenameElementQuickfix(elem, renameQuickFixDescription), new DisableInspectionToolAction(this) with LowPriorityAction),
      if (showAsError) ProblemHighlightType.GENERIC_ERROR else ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    )
  }

  private def isRegularClassParameter(elem: ScNamedElement): Boolean =
    elem.nameContext match {
      case e: ScModifierListOwner if e.getModifierList.modifiers.contains(ScalaModifier.Override) => false
      case p: ScClassParameter if p.getModifierList.accessModifier.isEmpty                        => true
      case _                                                                                      => false
    }

  private def isElementShadowing(elem: ScNamedElement) : Boolean =
    // Fields suspected of being shadowed are all fields belonging to the containing class or trait with the same name
    // as the element under inspection, but not itself, and for which we can get the name context implementing ScMember,
    // so we can later check its modifiers.
    Option(PsiTreeUtil.getParentOfType(elem, classOf[ScTypeDefinition])).exists { typeDefinition =>
      typeDefinition
        .allTermsByName(elem.name)
        .exists {
          case term: ScNamedElement =>
            lazy val isUsed =
              typeDefinition.extendsBlock.templateBody.exists { body =>
                val scope = new LocalSearchScope(body)
                ReferencesSearch.search(elem, scope).findFirst() != null
              }
            term.nameContext match {
              case s: ScVariable if !s.isPrivate && !s.isAbstract => isUsed
              case s: ScClassParameter if s.isVar && !s.isPrivate => isUsed
              case _ => false
            }
          case _ => false
        }
  }

  @BooleanBeanProperty
  private[shadow] var privateShadowCompilerOption: Boolean = true

  @BooleanBeanProperty
  private[shadow] var fatalWarningsCompilerOption: Boolean = true

  @Override
  override def createOptionsPanel(): JComponent = {
    val panel = new InspectionOptionsPanel(this)
    val compilerOptionCheckbox = panel.addCheckboxEx(
      ScalaInspectionBundle.message("private.shadow.compiler.option.label"),
      "privateShadowCompilerOption"
    )
    panel.addDependentCheckBox(
      ScalaInspectionBundle.message("private.shadow.fatal.warnings.label"),
      "fatalWarningsCompilerOption",
      compilerOptionCheckbox
    )
    panel
  }
}

private[shadow] object PrivateShadowInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("private.shadow.description")

  @Nls
  private val renameQuickFixDescription: String = ScalaInspectionBundle.message("private.shadow.rename.identifier")
}
