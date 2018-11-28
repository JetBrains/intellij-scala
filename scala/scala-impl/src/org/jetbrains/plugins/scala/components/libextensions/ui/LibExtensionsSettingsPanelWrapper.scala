package org.jetbrains.plugins.scala.components.libextensions.ui

import java.awt.BorderLayout
import java.io.File

import com.intellij.openapi.fileChooser.{FileChooser, FileChooserDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui._
import com.intellij.ui.components.{JBLabel, JBList}
import com.intellij.util.ui.{JBUI, UIUtil}
import javax.swing._
import org.jetbrains.plugins.scala.components.libextensions.LibraryExtensionsManager._
import org.jetbrains.plugins.scala.components.libextensions._

class LibExtensionsSettingsPanelWrapper(private val rootPanel: JPanel,
                                        private val project: Project) {

  private val libraryExtensionsManager = LibraryExtensionsManager.getInstance(project)

  // Exported components
  val enabledCB: JCheckBox = new JCheckBox("Enable loading external extensions", true)

  class LibraryListModel(val extensionsModel: LibraryDetailsModel) extends AbstractListModel[ExtensionJarData] {
    private val extensionsManager: LibraryExtensionsManager = libraryExtensionsManager
    override def getSize: Int = extensionsManager.getAvailableLibraries.length
    override def getElementAt(i: Int): ExtensionJarData = extensionsManager.getAvailableLibraries(i)
  }

  class LibraryDetailsModel(selectedDescriptor: Option[ExtensionJarData]) extends AbstractListModel[ExtensionDescriptor] {
    private val myExtensions = selectedDescriptor.flatMap(_.descriptor.getCurrentPluginDescriptor.map(_.extensions)).getOrElse(Nil).filter(_.isAvailable)
    override def getSize: Int = myExtensions.length
    override def getElementAt(i: Int): ExtensionDescriptor = myExtensions(i)
  }

  def build(): Unit = {
    import com.intellij.util.ui.UI

    rootPanel.setLayout(new BorderLayout())
    UIUtil.addBorder(rootPanel, JBUI.Borders.empty(10))

    val checkBoxes  = new JPanel()
    checkBoxes.setLayout(new BoxLayout(checkBoxes, BoxLayout.Y_AXIS))
    checkBoxes.add(UI.PanelFactory.panel(enabledCB)
      .withTooltip("IDEA will try to search for extra support for particular libraries in your project")
      .createPanel())

    val settingsPanel = new JPanel(new BorderLayout())
    settingsPanel.add(checkBoxes, BorderLayout.CENTER)
    rootPanel.add(settingsPanel, BorderLayout.PAGE_START)


    val detailsModel = new LibraryDetailsModel(None)
    val extensionsList = new JBList[ExtensionDescriptor](detailsModel)
    val extensionsPane = new JPanel(new BorderLayout())
    extensionsPane.add(ScrollPaneFactory.createScrollPane(extensionsList))
    extensionsList.setEmptyText("Select library from the list above")
    extensionsList.installCellRenderer { ext: ExtensionDescriptor =>
      val ExtensionDescriptor(_, impl, name, description, _) = ext
      val builder = new StringBuilder
      if (name.nonEmpty) builder.append(name) else builder.append(impl)
      if (description.nonEmpty) builder.append(s" - $description")
      new JBLabel(builder.mkString)
    }

    val libraryListModel = new LibraryListModel(detailsModel)
    val librariesList = new JBList[ExtensionJarData](libraryListModel)
    val toolbarDecorator = ToolbarDecorator.createDecorator(librariesList)

    toolbarDecorator.disableUpDownActions()
    toolbarDecorator.setRemoveAction { _ =>
      val descriptor = librariesList.getSelectedValue
      if (descriptor != null) {
        libraryExtensionsManager.removeExtension(descriptor)
        librariesList.setModel(new LibraryListModel(detailsModel))
        extensionsList.setModel(detailsModel)
      }
    }

    toolbarDecorator.setAddAction { _ =>
      val jar = FileChooser.chooseFile(
        new FileChooserDescriptor(false, false, true, true, false, false),
        project, null)
      try {
        libraryExtensionsManager.addExtension(new File(jar.getCanonicalPath))
        librariesList.setModel(new LibraryListModel(detailsModel))
      } catch {
        case ex: ExtensionException =>
          Messages.showErrorDialog(ex.getMessage, "Failed to load extension JAR")
        case ex: Exception =>
          Messages.showErrorDialog(ex.toString, "Failed to load extension JAR")
      }
    }

    librariesList.setEmptyText("No known extension libraries")
    librariesList.addListSelectionListener { event =>
      val libraries = libraryExtensionsManager.getAvailableLibraries
      val index = event.getFirstIndex
      val newData = if (index != -1 && index < libraries.size) {
        Some(libraries(index))
      } else None
      extensionsList.setModel(new LibraryDetailsModel(newData))
    }
    librariesList.installCellRenderer{ ld: ExtensionJarData =>
      val ExtensionJarData(LibraryDescriptor(name, _, description, vendor, version, _), file, _) = ld
      val builder = new StringBuilder
      if (vendor.nonEmpty) builder.append(s"($vendor) ")
      builder.append(s"$name $version")
      if (description.nonEmpty) builder.append(s" - $description")
      val label = new JBLabel(builder.mkString)
      label.setToolTipText(file.getAbsolutePath)
      label
    }
    val librariesPane = new JPanel(new BorderLayout())
    librariesPane.add(toolbarDecorator.createPanel())

    val listsPane = new JBSplitter(true, 0.6f)
    listsPane.setFirstComponent(librariesPane)
    listsPane.setSecondComponent(extensionsPane)

    UIUtil.addBorder(librariesPane,IdeBorderFactory.createTitledBorder("Known extension libraries", false))
    UIUtil.addBorder(extensionsPane, IdeBorderFactory.createTitledBorder("Extensions in selected library", false))

    enabledCB.addActionListener { _ =>
      libraryExtensionsManager.setEnabled(enabledCB.isSelected)
      val detailsModel = new LibraryDetailsModel(None)
      val libraryListModel = new LibraryListModel(detailsModel)
      extensionsList.setModel(detailsModel)
      librariesList.setModel(libraryListModel)
      UIUtil.setEnabled(listsPane, enabledCB.isSelected, true)
    }

    rootPanel.add(listsPane, BorderLayout.CENTER)

  }

}
