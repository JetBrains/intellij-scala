package org.jetbrains.plugins.scala
package overrideImplement

import javax.swing.{JCheckBox, JComponent}

import com.intellij.ide.util.MemberChooser
import com.intellij.psi.PsiClass
import com.intellij.ui.NonFocusableCheckBox
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import scala.collection.mutable

/**
 * Nikolay.Tropin
 * 2014-03-25
 */
class ScalaMemberChooser[T <: ClassMember : scala.reflect.ClassTag](elements: Array[T],
                         allowEmptySelection: Boolean,
                         allowMultiSelection: Boolean,
                         needAddOverrideChb: Boolean,
                         needSpecifyRetTypeChb: Boolean,
                         targetClass: ScTemplateDefinition)
        extends {
          val specifyRetTypeChb: JCheckBox = new NonFocusableCheckBox(ScalaBundle.message("specify.return.type.explicitly"))
          val addOverrideModifierChb = new NonFocusableCheckBox(ScalaBundle.message("add.override.modifier"))
          private val checkboxes = Array[JComponent](specifyRetTypeChb, addOverrideModifierChb)
          private val sortedElements = ScalaMemberChooser.sorted(elements, targetClass)
        } with MemberChooser[T](sortedElements.toArray[T], allowEmptySelection, allowMultiSelection, targetClass.getProject, null, checkboxes) {

  specifyRetTypeChb.setSelected(ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY)
  specifyRetTypeChb.setVisible(needSpecifyRetTypeChb)

  addOverrideModifierChb.setSelected(ScalaApplicationSettings.getInstance().ADD_OVERRIDE_TO_IMPLEMENTED)
  addOverrideModifierChb.setVisible(needAddOverrideChb)

  override def doOKAction(): Unit = {
    ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY = specifyRetTypeChb.isSelected
    ScalaApplicationSettings.getInstance.ADD_OVERRIDE_TO_IMPLEMENTED = addOverrideModifierChb.isSelected
    super.doOKAction()
  }
}

object ScalaMemberChooser {
  def sorted[T <: ClassMember](members: Seq[T], targetClass: ScTemplateDefinition): Seq[T] = {
    val groupedMembers = members.groupBy(cm => cm.getElement.getContainingClass)
    val sortedClasses = mutable.LinkedHashSet[PsiClass]()
    if (targetClass != null) {
      val supers = targetClass.supers
      sortedClasses ++= supers
    }
    sortedClasses ++= groupedMembers.keys.toSeq.sortWith((c1, c2) => c1.isInheritor(c2, true))

    sortedClasses.flatMap(c => groupedMembers.getOrElse(c, Seq.empty)).toSeq
  }
}
