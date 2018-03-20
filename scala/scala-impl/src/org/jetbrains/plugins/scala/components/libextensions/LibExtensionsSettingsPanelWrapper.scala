package org.jetbrains.plugins.scala.components.libextensions

import java.awt.BorderLayout

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.ui._
import com.intellij.ui.components.JBList
import com.intellij.util.ui.{JBUI, UIUtil}
import javax.swing._

class LibExtensionsSettingsPanelWrapper(private val rootPanel: JPanel, private val project: Project) {

  private val libraryExtensionsManager = LibraryExtensionsManager.getInstance(project)

  private val pauseAction = new AnActionButton("Toggle Ignore", AllIcons.Actions.Pause) {
    override def actionPerformed(e: AnActionEvent): Unit = ???
  }

  class LibraryListModel(val extensionsModel: LibraryDetailsModel) extends AbstractListModel[LibraryDescriptor] {
    private val extensionsManager: LibraryExtensionsManager = libraryExtensionsManager
    override def getSize: Int = extensionsManager.getAvailableLibraries.length
    override def getElementAt(i: Int) = extensionsManager.getAvailableLibraries(i)
  }


  class LibraryDetailsModel(selectedDescriptor: Option[LibraryDescriptor]) extends AbstractListModel[Extension] {
    private val myExtensions = selectedDescriptor.flatMap(_.getCurrentPluginDescriptor.map(_.flattenExtensions())).getOrElse(Nil)
    override def getSize: Int = myExtensions.length
    override def getElementAt(i: Int): Extension = myExtensions(i)
  }

  def build(): Unit = {
    import com.intellij.util.ui.UI

    rootPanel.setLayout(new BorderLayout())
    UIUtil.addBorder(rootPanel, JBUI.Borders.empty(10))

    val checkBoxes = new JPanel()
    checkBoxes.setLayout(new BoxLayout(checkBoxes, BoxLayout.Y_AXIS))
    checkBoxes.add(UI.PanelFactory.panel(new JCheckBox("Enable searching for extensions online", true))
      .withTooltip("BlahBlah").createPanel())
    checkBoxes.add(UI.PanelFactory.panel(new JCheckBox("Show only for this project", true))
      .withTooltip("BlahBlah").createPanel())

    val settingsPanel = new JPanel(new BorderLayout())
    settingsPanel.add(checkBoxes, BorderLayout.CENTER)
    settingsPanel.add(new JButton("Manage Search Patterns"), BorderLayout.LINE_END)

    rootPanel.add(settingsPanel, BorderLayout.PAGE_START)


    val detailsModel = new LibraryDetailsModel(None)
    val extensionsList = new JBList[Extension](detailsModel)
    val extensionsPane = ToolbarDecorator.createDecorator(extensionsList)
      .disableDownAction()
      .disableUpAction()
      .disableRemoveAction()
      .addExtraAction(pauseAction)
      .createPanel()
    extensionsList.setEmptyText("Select library from the list above")

    val libraryListModel = new LibraryListModel(detailsModel)
    val librariesList = new JBList[LibraryDescriptor](libraryListModel)
    librariesList.setEmptyText("No known extension libraries")
    librariesList.addListSelectionListener { event =>
      val newData = if (event.getFirstIndex != -1)
        Option(libraryExtensionsManager.getAvailableLibraries(event.getFirstIndex)) else None
      extensionsList.setModel(new LibraryDetailsModel(newData))
    }
    val librariesPane = ToolbarDecorator.createDecorator(librariesList)
      .disableDownAction()
      .disableUpAction()
      .disableRemoveAction()
      .addExtraAction(pauseAction)
      .createPanel()




    val listsPane = new JBSplitter(true, 0.6f)
    listsPane.setFirstComponent(librariesPane)
    listsPane.setSecondComponent(extensionsPane)

    UIUtil.addBorder(librariesPane,IdeBorderFactory.createTitledBorder("Known extension libraries", false))
    UIUtil.addBorder(extensionsPane, IdeBorderFactory.createTitledBorder("Extensions in selected library", false))

    rootPanel.add(listsPane, BorderLayout.CENTER)

  }

}
