package org.jetbrains.plugins.scala
package overrideImplement

import java.awt.FlowLayout
import javax.swing.{JComponent, JPanel}

import com.intellij.ide.util.MemberChooser
import com.intellij.psi.PsiClass
import com.intellij.ui.{HyperlinkLabel, NonFocusableCheckBox}
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.ThreeStateCheckBox.State
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil

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
                         needCopyScalaDocChb: Boolean,
                         targetClass: ScTemplateDefinition)
        extends {
          val settingsPanel = new JPanel()
          val typePanel = new JPanel()
   
          private val otherComponents = Array[JComponent](settingsPanel, typePanel)
          private val sortedElements = ScalaMemberChooser.sorted(elements, targetClass)
          
        } with MemberChooser[T](sortedElements.toArray[T], allowEmptySelection, allowMultiSelection, targetClass.getProject, null, otherComponents) {
  
  
  val addOverrideModifierChb = new NonFocusableCheckBox(ScalaBundle.message("add.override.modifier"))
  val copyScalaDocChb = new NonFocusableCheckBox(ScalaBundle.message("copy.scaladoc"))
  val mySpecifyTypeChb = new ThreeStateCheckBox(ScalaBundle.message("specify.return.type.explicitly"))
  
  setUpSettingsPanel()
  setUpTypePanel()
    
  override def doOKAction(): Unit = {
    ScalaApplicationSettings.getInstance.ADD_OVERRIDE_TO_IMPLEMENTED = addOverrideModifierChb.isSelected
    ScalaApplicationSettings.getInstance.COPY_SCALADOC = copyScalaDocChb.isSelected
    
    ScalaApplicationSettings.getInstance().SPECIFY_RETURN_TYPE_EXPLICITLY = {
      mySpecifyTypeChb.getState match {
        case State.SELECTED => ScalaApplicationSettings.ReturnTypeLevel.ADD
        case State.NOT_SELECTED => ScalaApplicationSettings.ReturnTypeLevel.REMOVE
        case State.DONT_CARE => ScalaApplicationSettings.ReturnTypeLevel.BY_CODE_STYLE
      }
    }
    
    super.doOKAction()
  }
  
  private def setUpSettingsPanel(): Unit ={
    settingsPanel.setLayout(new FlowLayout(FlowLayout.LEFT))
    settingsPanel.add(addOverrideModifierChb)
    settingsPanel.add(copyScalaDocChb)
  
    addOverrideModifierChb.setSelected(ScalaApplicationSettings.getInstance().ADD_OVERRIDE_TO_IMPLEMENTED)
    addOverrideModifierChb.setVisible(needAddOverrideChb)
  
    copyScalaDocChb.setSelected(ScalaApplicationSettings.getInstance().COPY_SCALADOC)
    copyScalaDocChb.setVisible(needCopyScalaDocChb)
  }
  
  private def setUpTypePanel(): JPanel ={
    typePanel.add(mySpecifyTypeChb)
  
    val myLinkContainer = new JPanel
    typePanel.add(myLinkContainer)
  
    myLinkContainer.add(setUpHyperLink())
    typePanel
  }
  
  private def setUpHyperLink(): HyperlinkLabel = {
    val link = TypeAnnotationUtil.createTypeAnnotationsHLink(targetClass.getProject, ScalaBundle.message("default.ta.settings"))
    link
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
    val ordering = new Ordering[PsiClass] {
      override def compare(c1: PsiClass, c2: PsiClass): Int = {
        val less = c1.isInheritor(c2, /*checkDeep =*/ true)
        val more = c2.isInheritor(c1, /*checkDeep =*/ true)
        if (less && more) 0 //it is possible to have cyclic inheritance for generic traits in scala
        else if (less) -1
        else 1
      }
    }
    sortedClasses ++= groupedMembers.keys.toSeq.sorted(ordering)

    sortedClasses.flatMap(c => groupedMembers.getOrElse(c, Seq.empty)).toSeq
  }
}
