package org.jetbrains.plugins.scala.codeInspection.shadow

import com.intellij.codeInspection.ex.DisableInspectionToolAction
import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import net.miginfocom.swing.MigLayout
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.ui.InspectionOptions
import org.jetbrains.plugins.scala.codeInspection.quickfix.RenameElementQuickfix
import org.jetbrains.plugins.scala.codeInspection.{AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps

import javax.swing.{JComponent, JLabel, JPanel}

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
        case e: ScMember if e.isLocal && localShadowing.isEnabled(elem) =>
          // if the field under inspection is local, it may shadow any field in the same class/trait, but only non-private fields in parent types
          suspects.exists { s => !s.isPrivate || findTypeDefinition(s).contains(typeDefinition) }
        case _ if mutableShadowing.isEnabled(elem) =>
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

  private val mutableShadowing =
    InspectionOptions(
      "mutableShadowing",
      ScalaInspectionBundle.message("suspicious.shadowing.mutable.label"),
      "-Xlint:private-shadow"
    )

  private val localShadowing =
    InspectionOptions(
      "localShadowing",
      ScalaInspectionBundle.message("suspicious.shadowing.local.label")
    )

  override def readSettings(node: Element): Unit = {
    super.readSettings(node)
    mutableShadowing.readSettings(node)
    localShadowing.readSettings(node)
  }

  override def writeSettings(node: Element): Unit = {
    mutableShadowing.writeSettings(node)
    localShadowing.writeSettings(node)
    super.writeSettings(node)
  }

  @Override
  override def createOptionsPanel(): JComponent = {
    val panel = new JPanel(new MigLayout("fillx, ins 0"))
    panel.add(new JLabel(ScalaInspectionBundle.message("suspicious.shadowing.label")), "spanx")
    panel.add(mutableShadowing.comboBox, "wrap, spanx")
    panel.add(localShadowing.checkBox, "wrap, spanx")
    panel
  }
}

object FieldShadowInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("suspicious.shadowing.description")

  @Nls
  private val renameQuickFixDescription: String = ScalaInspectionBundle.message("suspicious.shadowing.rename.identifier")

  private def findTypeDefinition(elem: PsiElement): Option[ScTypeDefinition] =
    Option(PsiTreeUtil.getParentOfType(elem, classOf[ScTypeDefinition]))
}
