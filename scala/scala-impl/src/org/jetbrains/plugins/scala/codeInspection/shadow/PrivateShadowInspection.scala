package org.jetbrains.plugins.scala.codeInspection.shadow

import com.intellij.codeInsight.intention.{HighPriorityAction, LowPriorityAction}
import com.intellij.codeInspection._
import com.intellij.codeInspection.ex.DisableInspectionToolAction
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.{checkbox, pane}
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiElementVisitor}
import org.jetbrains.annotations.{Nls, NonNls}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.codeInspection.quickfix.RenameElementQuickfix
import org.jetbrains.plugins.scala.codeInspection.ui.CompilerInspectionOptions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps

import scala.beans.BooleanBeanProperty
import scala.collection.mutable

final class PrivateShadowInspection extends LocalInspectionTool {

  import PrivateShadowInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = new PsiElementVisitor {
    override def visitElement(element: PsiElement): Unit = element match {
      case elem: ScNamedElement if
        isInspectionAllowed(elem, privateShadowCompilerOption, "-Xlint:private-shadow") &&
          isClassParamWithoutAccessModsAndOverride(elem) && isElementShadowing(elem) =>
        holder.registerProblem(createProblemDescriptor(elem, annotationDescription)(holder.getManager, isOnTheFly))
      case _ =>
    }
  }

  private def createProblemDescriptor(elem: ScNamedElement, @Nls description: String)
                                     (implicit manager: InspectionManager, isOnTheFly: Boolean): ProblemDescriptor = {
    val showAsError =
      fatalWarningsCompilerOption &&
        (isCompilerOptionPresent(elem, "-Xfatal-warnings") || isCompilerOptionPresent(elem, "-Werror"))

    val fixes = mutable.ArrayBuilder.make[LocalQuickFix]
    fixes.addOne(new RenameElementQuickfix(elem, renameQuickFixDescription) with HighPriorityAction)
    fixes.addOne(new DisableInspectionToolAction(this) with LowPriorityAction)
    if (!privateShadowCompilerOption)
      fixes.addOne(LocalQuickFix.from(new UpdateInspectionOptionFix(this, privateShadowPropertyName, ScalaInspectionBundle.message("fix.private.shadow.compiler.option.label"), true)))
    if (!fatalWarningsCompilerOption)
      fixes.addOne(LocalQuickFix.from(new UpdateInspectionOptionFix(this, fatalWarningsPropertyName, ScalaInspectionBundle.message("fix.private.shadow.fatal.warnings.label"), true)))
    manager.createProblemDescriptor(
      elem.nameId,
      description,
      isOnTheFly,
      fixes.result(),
      if (showAsError) ProblemHighlightType.GENERIC_ERROR else ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    )
  }

  private def isClassParamWithoutAccessModsAndOverride(elem: ScNamedElement): Boolean =
    elem.nameContext match {
      case e: ScModifierListOwner if e.getModifierList.modifiers.contains(ScalaModifier.Override) => false
      case p: ScClassParameter if p.getModifierList.accessModifier.isEmpty                        => true
      case _                                                                                      => false
    }

  private def isElementShadowing(elem: ScNamedElement): Boolean =
    // Fields suspected of being shadowed are all fields belonging to the containing class or trait with the same name
    // as the element under inspection, but not itself.
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

  override def getOptionsPane: OptPane = pane(
    checkbox(
      privateShadowPropertyName,
      ScalaInspectionBundle.message("private.shadow.compiler.option.label"),
      checkbox(fatalWarningsPropertyName, ScalaInspectionBundle.message("private.shadow.fatal.warnings.label"))
    )
  )
}

private[shadow] object PrivateShadowInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("private.shadow.description")

  @Nls
  private val renameQuickFixDescription: String = ScalaInspectionBundle.message("private.shadow.rename.identifier")

  @NonNls
  private val privateShadowPropertyName = "privateShadowCompilerOption"

  @NonNls
  private val fatalWarningsPropertyName = "fatalWarningsCompilerOption"
}
