package org.jetbrains.plugins.scala.codeInspection.shadow

import com.intellij.codeInspection.ex.DisableInspectionToolAction
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.quickfix.RenameElementQuickfix
import org.jetbrains.plugins.scala.codeInspection.{AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps

import javax.swing.JComponent
import scala.util.Try

final class FieldShadowInspection extends AbstractRegisteredInspection {
  import FieldShadowInspection._

  override protected def problemDescriptor(element:             PsiElement,
                                           maybeQuickFix:       Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType:       ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] =
    element match {
      case elem: ScNamedElement if isElementShadowing(elem) => Some(createProblemDescriptor(elem, annotationDescription))
      case _ => None
    }

  private lazy val disableInspectionToolAction = new DisableInspectionToolAction(this)

  private def createProblemDescriptor(elem: ScNamedElement, @Nls description: String)
                                     (implicit manager: InspectionManager, isOnTheFly: Boolean): ProblemDescriptor =
    manager.createProblemDescriptor(
      elem,
      description,
      isOnTheFly,
      Array[LocalQuickFix](new RenameElementQuickfix(elem, renameQuickFixDescription), disableInspectionToolAction),
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    )

  private def isElementShadowing(elem: ScNamedElement): Boolean =
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
        case e: ScMember if e.isLocal && shouldHighlight.highlightLocalShadowing =>
          // if the field under inspection is local, it may shadow any field in the same class/trait, but only non-private fields in parent types
          suspects.exists { s => !s.isPrivate || findTypeDefinition(s).contains(typeDefinition) }
        case _ if shouldHighlight.highlightMutableShadowing(elem) =>
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

  private var shouldHighlight: HighlightShadowing = defaultHighlightShadowing

  override def readSettings(node: Element): Unit = {
    super.readSettings(node)
    Try(node.getAttributeValue(highlightShadowingPropertyName).toInt).foreach { id =>
      shouldHighlight = getHighlightShadowingOption(id)
    }
  }

  override def writeSettings(node: Element): Unit = {
    node.setAttribute(highlightShadowingPropertyName, shouldHighlight.id.toString)
    super.writeSettings(node)
  }

  @Override
  override def createOptionsPanel(): JComponent =
    new ComboboxOptionsPanel[Int](
      highlightShadowingLabel,
      highlightVarShadowingOptions.map(option => option.id -> option.label),
      () => shouldHighlight.id,
      id => { shouldHighlight = getHighlightShadowingOption(id) }
    )
}

object FieldShadowInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("suspicious.shadowing.description")

  @Nls
  private val renameQuickFixDescription: String = ScalaInspectionBundle.message("suspicious.shadowing.rename.identifier")

  @Nls
  private val highlightShadowingLabel: String = ScalaInspectionBundle.message("suspicious.shadowing.label")

  private val highlightShadowingPropertyName = "shouldHighlight"

  sealed trait HighlightShadowing {
    val id: Int
    val label: String
    def highlightMutableShadowing(elem: ScNamedElement): Boolean
    def highlightLocalShadowing: Boolean = true
  }

  object HighlightShadowing {
    case object Always extends HighlightShadowing {
      override val id: Int = 0
      override val label: String = ScalaInspectionBundle.message("suspicious.shadowing.always")
      override def highlightMutableShadowing(elem: ScNamedElement): Boolean = true
    }

    case object Never extends HighlightShadowing {
      override val id: Int = 1
      override val label: String = ScalaInspectionBundle.message("suspicious.shadowing.never")
      override def highlightMutableShadowing(elem: ScNamedElement): Boolean = false
    }

    case object CheckCompilerOption extends HighlightShadowing {
      override val id: Int = 2
      override val label: String = ScalaInspectionBundle.message("suspicious.shadowing.check")
      override def highlightMutableShadowing(elem: ScNamedElement): Boolean =
        elem.module.exists(_.scalaCompilerSettings.additionalCompilerOptions.contains("-Xlint:private-shadow"))
    }

    case object OnlyCompilerOption extends HighlightShadowing {
      override val id: Int = 3
      override val label: String = ScalaInspectionBundle.message("suspicious.shadowing.only")
      override def highlightMutableShadowing(elem: ScNamedElement): Boolean =
        elem.module.exists(_.scalaCompilerSettings.additionalCompilerOptions.contains("-Xlint:private-shadow"))
      override def highlightLocalShadowing: Boolean = false
    }
  }

  private val highlightVarShadowingOptions: Seq[HighlightShadowing] = {
    import HighlightShadowing._
    Seq(Always, Never, CheckCompilerOption, OnlyCompilerOption)
  }

  private val defaultHighlightShadowing = HighlightShadowing.Always

  private def getHighlightShadowingOption(id: Int) =
    highlightVarShadowingOptions.find(_.id == id).getOrElse(defaultHighlightShadowing)

  private def findTypeDefinition(elem: PsiElement): Option[ScTypeDefinition] =
    Option(PsiTreeUtil.getParentOfType(elem, classOf[ScTypeDefinition]))
}
