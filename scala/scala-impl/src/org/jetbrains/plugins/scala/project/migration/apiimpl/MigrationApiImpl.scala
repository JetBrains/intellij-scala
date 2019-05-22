package org.jetbrains.plugins.scala.project.migration.apiimpl

import java.awt.{Component, Dimension}

import com.intellij.compiler.impl.CompilerErrorTreeView
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.{ProjectComponent, ServiceManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.wm.{ToolWindowId, ToolWindowManager}
import com.intellij.ui.content.{Content, ContentFactory, MessageView}
import com.intellij.uiDesigner.core.{GridConstraints, GridLayoutManager}
import javax.swing._
import org.jetbrains.plugins.scala.extensions
import org.jetbrains.plugins.scala.project.migration.api.{MigrationApiService, MigrationReport, SettingsDescriptor}
import org.jetbrains.plugins.scala.util.NotificationUtil

/**
  * User: Dmitry.Naydanov
  * Date: 07.09.16.
  */
class MigrationApiImpl(val project: Project) extends ProjectComponent with MigrationApiService {
  override def getComponentName: String = "ScalaLibraryMigrationApi"

  override def showPopup(txt: String, title: String, handler: String => Unit): Unit = {
    NotificationUtil.showMessage(
      project = project,
      message = txt,
      title = title,
      notificationType = NotificationType.INFORMATION,
      handler = handler
    )
  }

  override def showDialog(title: String, text: String, 
                          settingsDescriptor: SettingsDescriptor, 
                          onOk: SettingsDescriptor => Unit, onCancel: => Unit): Unit = {
    val elementsCount = 1 + settingsDescriptor.checkBoxes.size + settingsDescriptor.comboBoxes.size + settingsDescriptor.textFields.size
    val labelBase = new JPanel(new GridLayoutManager(2, 1))
    val base = new JPanel(new GridLayoutManager(elementsCount + 1, 2))
    val myCenterPanel = new JScrollPane(labelBase)
    

    def getElementConstraint(idx: Int, columnNum: Int = 0) =
      new GridConstraints(idx, columnNum, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
      GridConstraints.FILL_BOTH, GridConstraints.FILL_BOTH, new Dimension(-1, -1), new Dimension(-1, -1), new Dimension(-1, -1))

    def addElementToPanel(element: Component, idx: Int, column: Int = 0): Unit = base.add(element, getElementConstraint(idx, column))
    
    labelBase.add(new JLabel(text), getElementConstraint(0))
    labelBase.add(base, getElementConstraint(1))
    
    var currentIndex = 0
    
    def addSettingControl[T <: Component](control: T, name: String) = {
      addElementToPanel(new JLabel(name), currentIndex)
      addElementToPanel(control, currentIndex, 1)
      
      control setName name
      currentIndex += 1
      control
    }
    
    val checkBoxes = settingsDescriptor.checkBoxes.map {
      case (txt, defaultValue) => 
        val box = new JCheckBox()
        box.setSelected(defaultValue)
        addSettingControl(box, txt)
    }
    
    val comboBoxes = settingsDescriptor.comboBoxes.map {
      case (txt, variants) => 
        val box = new JComboBox[String](variants.toArray[String])
        addSettingControl(box, txt)
    }
    
    val textFields = settingsDescriptor.textFields.map {
      case (txt, defaultValue) => 
        val field = new JTextField(defaultValue)
        addSettingControl(field, txt)
    }


    val builder = new DialogBuilder(project)
    builder setOkActionEnabled true
    builder setCenterPanel myCenterPanel
    builder setTitle title
    
    builder.setOkOperation(() => {
      val result = SettingsDescriptor(
        checkBoxes.map {
          b => (b.getName, b.isSelected)
        },
        comboBoxes.map { //will make it multiple choice later
          b => (b.getName, Seq(b.getSelectedItem.toString))
        },
        textFields.map {
          f => (f.getName, f.getText)
        }
      )

      onOk(result)

      builder.getDialogWrapper.close(0, true)
    })
    
    builder.setCancelOperation(() => {
      onCancel

      builder.getDialogWrapper.close(0, false)
    })
    
    builder.show()
  }

  override def showReport(report: MigrationReport): Unit = {
    /**
      * Converts message type from api to the code of MessageCategory used by IDEA 
      * 
      * @see [[com.intellij.util.ui.MessageCategory]]
      */
    def convertMessageType(tpe: MigrationReport.MessageType) = tpe.code

    val myProject = project
    val messagesTree = new CompilerErrorTreeView(project, null)

    report.getAllMessages foreach {
      message =>
        val file = message.file.map(_.getVirtualFile).orNull
        
        if (file == null || (file != null && file.isValid)) {
          messagesTree.addMessage(convertMessageType(message.category), Array(message.text), file, 
            message.line.getOrElse(0), message.column.getOrElse(0), null)
        }
    }

    extensions.invokeLater {
      val messagesContent = ContentFactory.SERVICE.getInstance.createContent(messagesTree.getComponent, MigrationApiImpl.MY_MESSAGE_CONTENT_NAME, true)
      val contentManager = MessageView.SERVICE.getInstance(myProject).getContentManager

      contentManager addContent messagesContent
      contentManager setSelectedContent messagesContent

      MigrationApiImpl.openMessageView(myProject, messagesContent, messagesTree)
    }
  }
}

object MigrationApiImpl {
  private val MY_MESSAGE_CONTENT_NAME = "Scala migration messages"
  
  def getApiInstance(project: Project): MigrationApiService = project.getComponent[MigrationApiImpl](classOf[MigrationApiImpl])

  def openMessageView(project: Project, content: Content, treeView: CompilerErrorTreeView) {
    val commandProcessor = CommandProcessor.getInstance()
    commandProcessor.executeCommand(project, () => {
      val messageView = ServiceManager.getService(project, classOf[MessageView])
      messageView.getContentManager setSelectedContent content

      val toolWindow = ToolWindowManager getInstance project getToolWindow ToolWindowId.MESSAGES_WINDOW
      if (toolWindow != null) toolWindow.show(null)
    }, null, null)
  }
}
