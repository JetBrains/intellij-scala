package org.jetbrains.plugins.scala.codeInspection.shadow

import com.intellij.codeInspection.ex.DisableInspectionToolAction
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.{AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScMember, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps

import javax.swing.JComponent
import scala.annotation.tailrec

final class FieldShadowingInspection extends AbstractRegisteredInspection {
  import FieldShadowingInspection._

  override protected def problemDescriptor(element:             PsiElement,
                                           maybeQuickFix:       Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType:       ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] =
    element match {
      case elem: ScNamedElement if isElementShadowing(elem) => Some(createProblemDescriptor(elem, annotationDescription))
      case _ => None
    }

  private def createProblemDescriptor(elem: ScNamedElement, @Nls description: String)
                                     (implicit manager: InspectionManager, isOnTheFly: Boolean): ProblemDescriptor =
    manager.createProblemDescriptor(
      elem,
      description,
      isOnTheFly,
      Array[LocalQuickFix](new RenameElementQuickfix(elem, renameQuickFixDescription), new DisableInspectionToolAction(this)),
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    )

  def isElementShadowing(elem: ScNamedElement): Boolean =
    elem.nameContext match {
      case e: ScModifierListOwner if e.getModifierList.modifiers.contains(ScalaModifier.Override) =>
        false
      case _ =>
        findTypeDefinition(elem) match {
          case Some(typeDefinition) if isElementShadowing(elem, typeDefinition) => true
          case _  => false
        }
    }

  private def isElementShadowing(elem: ScNamedElement, typeDefinition: ScTypeDefinition) : Boolean = {
    // Fields suspected of being shadowed are all fields belonging to the containing class or trait with the same name
    // as the element under inspection, but not itself, and for which we can get the name context implementing ScMember,
    // so we can later check its modifiers.
    val suspects =
      typeDefinition
        .allTermsByName(elem.name)
        .collect { case term: ScNamedElement if !term.isEquivalentTo(elem) => term.nameContext }
        .collect { case nameContext: ScMember => nameContext }

    if (suspects.isEmpty)
      false
    else
      elem.nameContext match {
        case e: ScMember if e.isLocal =>
          // if the field under inspection is local, it may shadow any field in the same class/trait, but only non-private fields in parent types
          suspects.exists { s => !s.isPrivate || findTypeDefinition(s).contains(typeDefinition) }
        case _ if shouldHighlightVarShadowing(elem) =>
          // if the field under inspection is a class/trait field, it may shadow a non-private var from a parent type (see the compiler option -Xlint:private-shadow)
          suspects.exists {
            case s: ScVariable if !s.isPrivate => true
            case s: ScClassParameter if s.isVar && !s.isPrivate => true
            case _ => false
          }
        case _ =>
          // otherwise we assume that class/trait fields "shadowing" fields from parent types are in fact overriding them
          false
      }
  }

  private def shouldHighlightVarShadowing(elem: ScNamedElement): Boolean =
    highlightVarShadowing match {
      case HighlightVarShadowingAlways              => true
      case HighlightVarShadowingNever               => false
      case HighlightVarShadowingCheckCompilerOption =>
        elem.module.exists(_.additionalCompilerOptions.contains("-Xlint:private-shadow"))
    }

  var highlightVarShadowing: Int = HighlightVarShadowingAlways

  def getHighlightVarShadowing: Int = highlightVarShadowing

  def setHighlightVarShadowing(value: Int): Unit = {
    this.highlightVarShadowing = value
  }

  @Override
  override def createOptionsPanel(): JComponent =
    new ComboboxOptionsPanel(
      highlightVarShadowingLabel,
      this,
      "highlightVarShadowing",
      comboboxValues,
      defaultItem = highlightVarShadowing
    )
}

object FieldShadowingInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("suspicious.shadowing.of.a.field")

  @Nls
  val renameQuickFixDescription: String = ScalaInspectionBundle.message("suspicious.shadowing.rename.identifier")

  @Nls
  val highlightVarShadowingLabel: String = ScalaInspectionBundle.message("suspicious.shadowing.of.a.var.label")

  val HighlightVarShadowingAlways: Int = 0
  val HighlightVarShadowingNever: Int = 1
  val HighlightVarShadowingCheckCompilerOption: Int = 2

  val comboboxValues: Map[Int, String] =
    Map(
      HighlightVarShadowingAlways              -> ScalaInspectionBundle.message("suspicious.shadowing.of.a.var.always"),
      HighlightVarShadowingNever               -> ScalaInspectionBundle.message("suspicious.shadowing.of.a.var.never"),
      HighlightVarShadowingCheckCompilerOption -> ScalaInspectionBundle.message("suspicious.shadowing.of.a.var.check")
    )

  @tailrec
  private def findTypeDefinition(elem: PsiElement): Option[ScTypeDefinition] =
    Option(elem) match {
      case Some(t: ScTypeDefinition) => Some(t)
      case Some(e: PsiElement)       => findTypeDefinition(e.getContext)
      case None                      => None
    }
}
