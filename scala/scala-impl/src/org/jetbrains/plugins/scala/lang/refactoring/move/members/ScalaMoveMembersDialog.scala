package org.jetbrains.plugins.scala.lang.refactoring.move.members

import java.awt.BorderLayout

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiDocumentManager, PsiMember}
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.move.moveMembers.{MoveMembersOptions, MoveMembersProcessor}
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.EditorComboBox
import javax.swing._
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaFileType}

class ScalaMoveMembersDialog(project: Project, canBeParent: Boolean, sourceObject: ScObject, memberToMove: ScMember) extends RefactoringDialog(project, canBeParent) {

  private val targetObjectFragment: ScalaCodeFragment = {
    val fragment = new ScalaCodeFragment(project, "")
    fragment.setContext(memberToMove.getContext, memberToMove)
    fragment
  }

  private val myTfTargetClassName: EditorComboBox = {
    val document = PsiDocumentManager.getInstance(project).getDocument(targetObjectFragment)
    new EditorComboBox(document, project, ScalaFileType.INSTANCE)
  }

  init()

  override def doAction(): Unit = {
    val reference =
      PsiTreeUtil.getChildOfType(targetObjectFragment, classOf[ScReferenceElement])
        .toOption.toRight(ScalaBundle.message("move.members.object.name.or.qualified.name.expected"))

    val maybeObj = reference.flatMap(_.resolve()
      .asOptionOf[ScObject].toRight(ScalaBundle.message("move.members.cannot.find.object")))

    maybeObj match {
      case Right(obj) =>
        val processor = ScalaMoveMembersDialog.createProcessor(obj, memberToMove)
        invokeRefactoring(processor)
      case Left(message) =>
        Messages.showErrorDialog(message, RefactoringBundle.message("error.title"))
    }
  }

  override def createCenterPanel(): JComponent = {
    val panel = new JPanel(new BorderLayout)

    val box = Box.createVerticalBox

    val _panel1 = new JPanel(new BorderLayout)
    val sourceClassField = new JTextField
    sourceClassField.setText(sourceObject.name)
    sourceClassField.setEditable(false)
    _panel1.add(new JLabel(ScalaBundle.message("move.members.source.title")), BorderLayout.NORTH)
    _panel1.add(sourceClassField, BorderLayout.CENTER)
    box.add(_panel1)

    box.add(Box.createVerticalStrut(10))

    val _panel2 = new JPanel(new BorderLayout)
    val label = new JLabel(ScalaBundle.message("move.members.target.title"))

    label.setLabelFor(myTfTargetClassName)
    _panel2.add(label, BorderLayout.NORTH)
    _panel2.add(myTfTargetClassName, BorderLayout.CENTER)
    box.add(_panel2)

    panel.add(box, BorderLayout.CENTER)
    panel.add(Box.createVerticalStrut(10), BorderLayout.SOUTH)

    validateButtons()
    panel
  }

  override def getPreferredFocusedComponent: JComponent = myTfTargetClassName

}

object ScalaMoveMembersDialog {
  def createProcessor(obj: ScObject, member: ScMember): MoveMembersProcessor = {
    //noinspection ScalaWrongMethodsUsage
    val qualName = obj.getQualifiedName
    new MoveMembersProcessor(obj.getProject, null, new MoveMembersOptions() {
      override def getMemberVisibility: String = "public"

      override def makeEnumConstant: Boolean = false

      override def getSelectedMembers: Array[PsiMember] = Array(member)

      override def getTargetClassName: String = qualName
    })
  }
}