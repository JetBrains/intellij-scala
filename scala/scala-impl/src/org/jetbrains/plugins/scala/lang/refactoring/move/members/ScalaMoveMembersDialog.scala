package org.jetbrains.plugins.scala.lang.refactoring.move.members

import java.awt.BorderLayout

import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.{JavaCodeFragment, PsiMember}
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.move.moveMembers.{MoveMembersOptions, MoveMembersProcessor}
import com.intellij.refactoring.ui.RefactoringDialog
import com.intellij.ui.{EditorComboBox, JavaReferenceEditorUtil}
import javax.swing._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

class ScalaMoveMembersDialog(project: Project, canBeParent: Boolean, sourceObject: ScObject, initialTargetMember: PsiMember) extends RefactoringDialog(project, canBeParent) {



  val myTfTargetClassName = new EditorComboBox(
    JavaReferenceEditorUtil.createDocument(
      "",
      myProject,
      true,
      JavaCodeFragment.VisibilityChecker.PROJECT_SCOPE_VISIBLE),
    myProject,
    StdFileTypes.HTML)

  init()


  override def doAction(): Unit = {
    val myMoveCallback = null

    val className = myTfTargetClassName.getText

    findClass(className) match {
      case Some(_) =>
        val mm = new MoveMembersProcessor(getProject, myMoveCallback, new MoveMembersOptions() {
          override def getMemberVisibility: String = "public"

          override def makeEnumConstant: Boolean = false

          override def getSelectedMembers: Array[PsiMember] = List(initialTargetMember).toArray

          override def getTargetClassName: String = className
        })
        invokeRefactoring(mm)
      case _ => Messages.showErrorDialog(ScalaBundle.message("move.members.target.must.be.object"), RefactoringBundle.message("error.title"))
    }
  }

  private def findClass(className: String): Option[ScObject] = {
    val indexOfDollar = className.indexOf("$")
    val classNameTocSearch = if(indexOfDollar < 0) className else className.substring(0, indexOfDollar)
    ElementScope(myProject).getCachedObject(classNameTocSearch)

  }

  override def createCenterPanel(): JComponent = {
    val panel = new JPanel(new BorderLayout)

    val box = Box.createVerticalBox

    val _panel1 = new JPanel(new BorderLayout)
    val sourceClassField = new JTextField
    sourceClassField.setText(sourceObject.name)
    sourceClassField.setEditable(false)
    _panel1.add(new JLabel(RefactoringBundle.message("move.members.move.members.from.label")), BorderLayout.NORTH)
    _panel1.add(sourceClassField, BorderLayout.CENTER)
    box.add(_panel1)

    box.add(Box.createVerticalStrut(10))

    val _panel2 = new JPanel(new BorderLayout)
    val label = new JLabel(RefactoringBundle.message("move.members.to.fully.qualified.name.label"))

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