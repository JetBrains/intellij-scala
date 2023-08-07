package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.ide.util.MemberChooser
import com.intellij.psi._
import com.intellij.ui.{HyperlinkLabel, NonFocusableCheckBox}
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.ThreeStateCheckBox.State
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.settings.annotations.{Declaration, Location, ScalaTypeAnnotationSettings}
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil._

import java.awt.FlowLayout
import javax.swing.event.{HyperlinkEvent, TreeSelectionEvent, TreeSelectionListener}
import javax.swing.{JCheckBox, JComponent, JPanel}
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class ScalaMemberChooser[T <: ClassMember : scala.reflect.ClassTag](elements: ArraySeq[T],
                                                                    allowEmptySelection: Boolean,
                                                                    allowMultiSelection: Boolean,
                                                                    needAddOverrideChb: Boolean,
                                                                    needSpecifyTypeChb: Boolean,
                                                                    needCopyScalaDocChb: Boolean,
                                                                    targetClass: ScTemplateDefinition,
                                                                    // hack early initializers
                                                                    addOverrideModifierChb: JCheckBox = new NonFocusableCheckBox(ScalaBundle.message("add.override.modifier")),
                                                                    copyScalaDocChb: JCheckBox = new NonFocusableCheckBox(ScalaBundle.message("copy.scaladoc")),
                                                                    typePanel: JPanel = new JPanel())
  extends MemberChooser[T](
    ScalaMemberChooser.sorted(elements, targetClass).toArray[T],
    allowEmptySelection,
    allowMultiSelection,
    targetClass.getProject,
    null,
    Array[JComponent](copyScalaDocChb, addOverrideModifierChb, typePanel)
  ) {

  val mySpecifyTypeChb = new ThreeStateCheckBox(ScalaBundle.message("specify.return.type.explicitly"))

  setUpSettingsPanel()
  setUpTypePanel()
  trackSelection()

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
    typePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0))

    addOverrideModifierChb.setSelected(ScalaApplicationSettings.getInstance().ADD_OVERRIDE_TO_IMPLEMENTED)
    addOverrideModifierChb.setVisible(needAddOverrideChb)

    copyScalaDocChb.setSelected(ScalaApplicationSettings.getInstance().COPY_SCALADOC)
    copyScalaDocChb.setVisible(needCopyScalaDocChb)
  }

  private def setUpTypePanel(): JPanel ={
    updateSpecifyTypeChb()
    typePanel.add(mySpecifyTypeChb)

    val myLinkContainer = new JPanel
    myLinkContainer.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0))
    typePanel.add(myLinkContainer)

    myLinkContainer.add(setUpHyperLink())
    typePanel.setVisible(needSpecifyTypeChb)
    typePanel
  }

  private def setUpHyperLink(): HyperlinkLabel = {
    val link = createTypeAnnotationsHLink(targetClass.getProject, ScalaBundle.message("default.ta.settings"))
    link.addHyperlinkListener((_: HyperlinkEvent) => invokeLater(updateSpecifyTypeChb()))
    link
  }

  private def updateSpecifyTypeChb(): Unit = mySpecifyTypeChb.setState(computeCheckBoxState)

  private def trackSelection(): Unit = {
    val selectionListener = new TreeSelectionListener {
      override def valueChanged(e: TreeSelectionEvent): Unit = {
        updateSpecifyTypeChb()
      }
    }

    // Reorder listeners. We need to know information about current selected element,
    // but, if we add new listener it will be called before MemberChooser listener,
    // and mySelectedElements in our listener will have previous information
    val listeners = myTree.getTreeSelectionListeners
    listeners.foreach(myTree.removeTreeSelectionListener)
    myTree.addTreeSelectionListener(selectionListener)
    listeners.foreach(myTree.addTreeSelectionListener)
  }

  private def computeCheckBoxState: ThreeStateCheckBox.State = {

    if (mySelectedElements == null)
      return State.DONT_CARE

    val elements = mySelectedElements.asScala.collect {
      case m: ClassMember => m.getElement
    }

    elements.map(typeAnnotationNeeded).toSeq.distinct match {
      case Seq(true) => State.SELECTED
      case Seq(false) => State.NOT_SELECTED
      case _ => State.DONT_CARE
    }
  }

  private def typeAnnotationNeeded(element: PsiElement): Boolean =
    ScalaTypeAnnotationSettings(element.getProject).isTypeAnnotationRequiredFor(
      Declaration(element),
      new Location.InsideClassLocation(targetClass),
    )
}

object ScalaMemberChooser {
  def sorted[T <: ClassMember](members: Seq[T], targetClass: ScTemplateDefinition): Seq[T] = {
    val result = sortedImpl(members, targetClass, groupExtensionMethods = false)
    //We are sure that original members instances won't be modified, just sorted
    result.asInstanceOf[Seq[T]]
  }

  def sortedWithExtensionMethodsGrouped[T <: ClassMember](members: Seq[T], targetClass: ScTemplateDefinition): Seq[ClassMember0] =
    sortedImpl(members, targetClass, groupExtensionMethods = true)

  private def sortedImpl[T <: ClassMember](
    members: Seq[T],
    targetClass: ScTemplateDefinition,
    groupExtensionMethods: Boolean
  ): Seq[ClassMember0] = {
    val clazzToMembers: Map[PsiClass, Seq[T]] =
      members.groupBy(cm => cm.getElement.getContainingClass)

    val sortedClasses: mutable.Set[PsiClass] =
      mutable.LinkedHashSet()

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
    val groupedMembersSorted = clazzToMembers.keys.toSeq.sorted(ordering)
    sortedClasses ++= groupedMembersSorted

    sortedClasses.flatMap { c =>
      val membersFromClass: Seq[T] = clazzToMembers.getOrElse(c, Seq.empty)
      val membersFromClassMaybeGrouped =
        if (groupExtensionMethods)
          doGroupExtensionMethods(membersFromClass)
        else
          membersFromClass

      membersFromClassMaybeGrouped.sortBy(_.getElement.getTextOffset)
    }.toSeq
  }

  /**
   * Replace all instances of `ScExtensionMethodMember` belonging to the same extension
   * with an instance of `ScExtensionMember`, which groups all extension methods
   */
  private def doGroupExtensionMethods[T <: ClassMember](members: Seq[T]): Seq[ClassMember0] = {
    val extensionsGrouped: Map[Option[ScExtension], Seq[T]] =
      members.groupBy { m =>
        val extensionMethod = m.asOptionOfUnsafe[ScExtensionMethodMember]
        val extension = extensionMethod.flatMap(_.signature.extensionSignature.map(_.extension))
        extension
      }

    extensionsGrouped.toSeq.flatMap {
      case (None, nonExtensions) =>
        nonExtensions
      case (Some(extension), members) =>
        val extensionMethodsSorted = members.asInstanceOf[Seq[ScExtensionMethodMember]].sortBy(_.getElement.getTextOffset)
        Seq(ScExtensionMember(extension, extensionMethodsSorted))
    }
  }
}
