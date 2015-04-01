//package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter
//
//import java.awt.{BorderLayout, GridBagConstraints, GridBagLayout, Insets}
//import javax.swing.border.EmptyBorder
//import javax.swing.{JComponent, JLabel, JPanel}
//
//import com.intellij.openapi.editor.Document
//import com.intellij.openapi.project.Project
//import com.intellij.psi.PsiDocumentManager
//import com.intellij.refactoring.changeSignature.MethodDescriptor
//import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
//import com.intellij.ui.EditorTextField
//import com.intellij.util.IJSwingUtilities
//import org.jetbrains.plugins.scala.lang.refactoring.changeSignature._
//
///**
// * @author Nikolay.Tropin
// */
//class ScalaIntroduceParameterNewDialog(project: Project, method: ScalaMethodDescriptor, parameter: ScalaParameterInfo)
//        extends ScalaChangeSignatureDialog(project, method) {
//
//  override def init(): Unit = {
//    super.init()
//    setTitle(ScalaIntroduceParameterHandler.REFACTORING_NAME)
//  }
//
//  override def createNorthPanel(): JComponent = {
//    val panel: JPanel = new JPanel(new GridBagLayout)
//    val gbc: GridBagConstraints = new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0)
//
//    def addNamePanel(panel: JPanel, gbc: GridBagConstraints): Unit = {
//      myNamePanel = new JPanel(new BorderLayout(0, 2))
//      myNameField = new EditorTextField(myMethod.getName)
//      val nameLabel: JLabel = new JLabel("&Name:")
//      nameLabel.setLabelFor(myNameField)
//      myNameField.setEnabled(myMethod.canChangeName)
//      myNameField.addDocumentListener(mySignatureUpdater)
//      myNameField.setPreferredWidth(200)
//      myNamePanel.add(nameLabel, BorderLayout.NORTH)
//      IJSwingUtilities.adjustComponentsOnMac(nameLabel, myNameField)
//      myNamePanel.add(myNameField, BorderLayout.SOUTH)
//
//      panel.add(myNamePanel, gbc)
//      gbc.gridx += 1
//    }
//
//    addNamePanel(panel, gbc)
//
//    if (myMethod.canChangeReturnType ne MethodDescriptor.ReadWriteOption.None) {
//      val typePanel: JPanel = new JPanel(new BorderLayout(0, 2))
//      typePanel.setBorder(new EmptyBorder(0, 0, 0, 8))
//      val typeLabel: JLabel = new JLabel("&Type:")
//      myReturnTypeCodeFragment = createReturnTypeCodeFragment
//      val document: Document = PsiDocumentManager.getInstance(myProject).getDocument(myReturnTypeCodeFragment)
//      myReturnTypeField = createReturnTypeTextField(document)
//      (myVisibilityPanel.asInstanceOf[ComboBoxVisibilityPanel[_]]).registerUpDownActionsFor(myReturnTypeField)
//      typeLabel.setLabelFor(myReturnTypeField)
//      if (myMethod.canChangeReturnType eq MethodDescriptor.ReadWriteOption.ReadWrite) {
//        myReturnTypeField.setPreferredWidth(200)
//        myReturnTypeField.addDocumentListener(mySignatureUpdater)
//      }
//      else {
//        myReturnTypeField.setEnabled(false)
//      }
//      typePanel.add(typeLabel, BorderLayout.NORTH)
//      IJSwingUtilities.adjustComponentsOnMac(typeLabel, myReturnTypeField)
//      typePanel.add(myReturnTypeField, BorderLayout.SOUTH)
//      panel.add(typePanel, gbc)
//      gbc.gridx += 1
//    }
//
//    panel.add(myNamePanel, gbc)
//
//    return panel
//
//
//  }
//}
