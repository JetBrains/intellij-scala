package org.jetbrains.plugins.scala
package overrideImplement

import javax.swing.{JComponent, JCheckBox}
import com.intellij.ui.NonFocusableCheckBox
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import com.intellij.ide.util.MemberChooser
import com.intellij.openapi.project.Project
import java.util.Comparator

/**
 * Nikolay.Tropin
 * 2014-03-25
 */
class ScalaMemberChooser[T <: ClassMember](elements: Array[T],
                         allowEmptySelection: Boolean,
                         allowMultiSelection: Boolean,
                         needAddOverrideChb: Boolean,
                         needSpecifyRetTypeChb: Boolean,
                         project: Project)
        extends {
          val specifyRetTypeChb: JCheckBox = new NonFocusableCheckBox(ScalaBundle.message("specify.return.type.explicitly"))
          val addOverrideModifierChb = new NonFocusableCheckBox(ScalaBundle.message("add.override.modifier"))
          private val checkboxes = Array[JComponent](specifyRetTypeChb, addOverrideModifierChb)
        } with MemberChooser[T](elements, allowEmptySelection, allowMultiSelection, project, null, checkboxes) {

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
