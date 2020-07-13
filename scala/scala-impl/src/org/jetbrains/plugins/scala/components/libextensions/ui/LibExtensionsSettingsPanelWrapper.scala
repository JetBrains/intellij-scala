package org.jetbrains.plugins.scala.components.libextensions.ui

import java.awt.BorderLayout
import java.io.File

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileChooser.{FileChooser, FileChooserDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui._
import com.intellij.ui.components.{JBLabel, JBList}
import com.intellij.util.ui.{JBUI, UIUtil}
import javax.swing._
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.components.libextensions.LibraryExtensionsManager._
import org.jetbrains.plugins.scala.components.libextensions._

class LibExtensionsSettingsPanelWrapper(private val rootPanel: JPanel,
                                        private val project: Project) {

  private val libraryExtensionsManager = LibraryExtensionsManager.getInstance(project)
  @NonNls
  private val CustomMacrosSupportHelpLink = "https://blog.jetbrains.com/scala/2015/10/14/intellij-api-to-build-scala-macros-support/"

  // Exported components
  val enabledCB: JCheckBox = new JCheckBox(ScalaBundle.message("enable.loading.external.extensions"), true)

  class LibraryListModel(val extensionsModel: LibraryDetailsModel) extends AbstractListModel[ExtensionJarData] {
    private val extensionsManager: LibraryExtensionsManager = libraryExtensionsManager
    override def getSize: Int = extensionsManager.getAvailableLibraries.length
    override def getElementAt(i: Int): ExtensionJarData = extensionsManager.getAvailableLibraries(i)
  }

  class LibraryDetailsModel(selectedDescriptor: Option[ExtensionJarData]) extends AbstractListModel[ExtensionDescriptor] {
    override def getSize: Int = myExtensions.length
    override def getElementAt(i: Int): ExtensionDescriptor = myExtensions(i)
    private val myExtensions = selectedDescriptor
      .flatMap(
        _.descriptor
          .getCurrentPluginDescriptor
          .map(_.extensions))
      .getOrElse(Nil)
      .filter(_.isAvailable)
  }

  def build(): Unit = {
    import com.intellij.util.ui.UI

    rootPanel.setLayout(new BorderLayout())
    UIUtil.addBorder(rootPanel, JBUI.Borders.empty(10))

    val checkBoxes  = new JPanel()
    checkBoxes.setLayout(new BoxLayout(checkBoxes, BoxLayout.Y_AXIS))
    checkBoxes.add(UI.PanelFactory.panel(enabledCB)
      .withTooltip(ScalaBundle.message("idea.will.try.to.search.for.extra.support.for.particular.libraries"))
      .withTooltipLink(
        ScalaBundle.message("how.to.add.custom.macro.support.help.link.title"),
        () => BrowserUtil.browse(CustomMacrosSupportHelpLink)
      )
      .createPanel())

    val settingsPanel = new JPanel(new BorderLayout())
    settingsPanel.add(checkBoxes, BorderLayout.CENTER)
    rootPanel.add(settingsPanel, BorderLayout.PAGE_START)


    val detailsModel = new LibraryDetailsModel(None)
    val extensionsList = new JBList[ExtensionDescriptor](detailsModel)
    val extensionsPane = new JPanel(new BorderLayout())
    extensionsPane.add(ScrollPaneFactory.createScrollPane(extensionsList))
    extensionsList.setEmptyText(ScalaBundle.message("select.library.from.the.list.above"))
    extensionsList.installCellRenderer { (ext: ExtensionDescriptor) =>
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
      if (jar != null)
        try {
          libraryExtensionsManager.addExtension(new File(jar.getCanonicalPath))
          librariesList.setModel(new LibraryListModel(detailsModel))
        } catch {
          case ex: ExtensionException =>
            //noinspection ReferencePassedToNls
            Messages.showErrorDialog(ex.getMessage, ScalaBundle.message("title.failed.to.load.extension.jar"))
          case ex: Exception =>
            //noinspection ReferencePassedToNls
            Messages.showErrorDialog(ex.toString, ScalaBundle.message("title.failed.to.load.extension.jar"))
        }
    }

    librariesList.setEmptyText(ScalaBundle.message("no.known.extension.libraries"))
    librariesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    librariesList.addListSelectionListener { event =>
      if (!event.getValueIsAdjusting) {
        val jarData = librariesList.getSelectedValue
        val model   = new LibraryDetailsModel(Option(jarData))
        extensionsList.setModel(model)
      }
    }
    librariesList.installCellRenderer{ (ld: ExtensionJarData) =>
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

    UIUtil.addBorder(librariesPane,IdeBorderFactory.createTitledBorder(ScalaBundle.message("known.extension.libraries"), false))
    UIUtil.addBorder(extensionsPane, IdeBorderFactory.createTitledBorder(ScalaBundle.message("extensions.in.selected.library"), false))

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
