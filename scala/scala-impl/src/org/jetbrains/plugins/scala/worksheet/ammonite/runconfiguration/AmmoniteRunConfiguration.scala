package org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration

import java.io.{File, IOException}

import javax.swing.event.{HyperlinkEvent, HyperlinkListener}
import javax.swing.{BoxLayout, JComponent, JPanel}
import com.intellij.execution.Executor
import com.intellij.execution.configurations._
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.impl.EditConfigurationsDialog
import com.intellij.execution.process.{ProcessHandler, ProcessNotCreatedException}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.{LabeledComponent, TextFieldWithBrowseButton}
import com.intellij.openapi.util.{JDOMExternalizer, SystemInfo}
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.RawCommandLineEditor
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.worksheet.ammonite.AmmoniteScriptWrappersHolder
import org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration.AmmoniteRunConfiguration.{AmmNotFoundException, MyEditor}

class AmmoniteRunConfiguration(project: Project, factory: ConfigurationFactory) extends
  RunConfigurationBase[Element](project, factory, AmmoniteRunConfiguration.AMMONITE_RUN_NAME) {

  def this(project: Project, factory: ConfigurationFactory, name: String) {
    this(project, factory)
    setFilePath(name)
  }

  private var execName: Option[String] = None
  private var fileName: Option[String] = None
  private var scriptParameters: Option[String] = None

  def setFilePath(path: String): Unit = {
    fileName = Option(path).filter(_ != "")
  }

  def setExecPath(path: String): Unit = {
    execName = Option(path)
  }

  def setScriptParameters(params: String): Unit = {
    scriptParameters = Option(params).filter(_ != "")
  }

  def getIOFile: Option[File] = fileName.map(new File(_)).filter(_.exists())

  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new MyEditor

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = {
    def patchSdkVersion(cmd: GeneralCommandLine): Unit = {
      Option(ProjectRootManager.getInstance(project).getProjectSdk).foreach {
        sdk => cmd.getEnvironment.put("JAVA_HOME", sdk.getHomePath)
      }
    }

    val state = new CommandLineState(environment) {
      override def startProcess(): ProcessHandler = {
        val cmd = new GeneralCommandLine()

        execName match {
          case Some(exec) =>
            val exFile = new File(exec)
            if (exFile.exists()) cmd.setExePath(exFile.getAbsolutePath) else cmd.setExePath(exec)
          case None =>
            cmd.setExePath("/usr/local/bin/amm")
        }

        if (isRepl) {
          cmd.addParameter("--predef")
          cmd.addParameter(s"${createPredefFile(getIOFile.map(_.getName).getOrElse("AmmoniteFile.sc"))}")
        }

        fileName.foreach{
          f =>
            cmd.addParameter(f)
            val tf = new File(f)
            if (tf.exists()) cmd.setWorkDirectory(tf.getParentFile)
        }
        scriptParameters.foreach(cmd.getParametersList.addParametersString(_))

        patchSdkVersion(cmd)

        try {
          JavaCommandLineStateUtil.startProcess(cmd)
        } catch {
          case pne: ProcessNotCreatedException =>
            pne.getCause match {
              case ioe: IOException =>
                ioe.getCause match {
                  case ioe2: IOException if ioe2.getMessage.contains("error=2") =>
                    throw new AmmNotFoundException(
                      s"<br>Can't find Ammonite:<br> ${ioe2.getMessage} <br>" + "<br> <a href=\"azaza\">Specify amm executable path?</a>",
                      pne.getCommandLine,
                      project
                    )
                  case _ =>
                }
              case _ =>
            }

            throw pne
        }
      }
    }
    state.setConsoleBuilder(new TextConsoleBuilderImpl(project))

    fileName.foreach {
      fn =>
        Option(LocalFileSystem.getInstance().findFileByIoFile(new File(fn))) foreach {
          vFile =>
            AmmoniteScriptWrappersHolder.getInstance(project).onAmmoniteRun(vFile)
        }
    }
    state
  }

  override def writeExternal(element: Element): Unit = {
    super.writeExternal(element)

    def saveFileElement(name: String, maybeString: Option[String]): Unit = {
      JDOMExternalizer.write(element, name, maybeString.getOrElse(""))
    }

    saveFileElement("execName", execName)
    saveFileElement("fileName", fileName)
    saveFileElement("scriptParameters", scriptParameters)
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)

    def retrieveFileElement(name: String): Option[String] = {
      JDOMExternalizer.readString(element, name) match {
        case "" | null => None
        case other => Option(other)
      }
    }

    execName = retrieveFileElement("execName")
    fileName = retrieveFileElement("fileName")
    scriptParameters = retrieveFileElement("scriptParameters")
  }

  private def createPredefFile(baseName: String): String = {
    val tempDir = new File(FileUtil.getTempDirectory)
    if (tempDir.exists()) tempDir.mkdir()

    val tempFile = new File(tempDir, s"predef$baseName")
    if (!tempFile.exists()) {
      tempFile.createNewFile()
      FileUtil.writeToFile(tempFile, s"repl.frontEnd() = ammonite.repl.FrontEnd.${if (SystemInfo.isWindows) "JLineWindows" else "JLineUnix"}")
    }

    tempFile.getAbsolutePath
  }

  private def isRepl = fileName.isEmpty
}

object AmmoniteRunConfiguration {
  val AMMONITE_RUN_NAME: String = "Ammonite Script"

  class AmmNotFoundException(message: String, commandLine: GeneralCommandLine, project: Project) extends ProcessNotCreatedException(message, commandLine) with HyperlinkListener {
    override def hyperlinkUpdate(e: HyperlinkEvent): Unit = {
      if (e.getEventType != HyperlinkEvent.EventType.ACTIVATED) return

      new EditConfigurationsDialog(project).show()
    }
  }

  private class MyEditor extends SettingsEditor[AmmoniteRunConfiguration]() {
    private val form = new AmmoniteRunConfigurationForm

    override def createEditor(): JComponent = form.panel

    override def applyEditorTo(s: AmmoniteRunConfiguration): Unit = {
      val (fileName, execName, scriptParameters) = form.get
      s.setFilePath(fileName)
      s.setExecPath(execName)
      s.setScriptParameters(scriptParameters)
    }

    override def resetEditorFrom(s: AmmoniteRunConfiguration): Unit = {
      form(s.fileName.getOrElse(""), s.execName.getOrElse("amm"), s.scriptParameters.getOrElse(""))
    }
  }

  private class AmmoniteRunConfigurationForm {
    val (panel, fileNameField, execNameField, scriptParameters) = initPanel()

    def get: (String, String, String) = (fileNameField.getText, execNameField.getText, scriptParameters.getText)

    def apply(fileName: String, execName: String, parametersRaw: String): Unit = {
      //noinspection ReferencePassedToNls
      fileNameField.setText(fileName)
      //noinspection ReferencePassedToNls
      execNameField.setText(execName)
      scriptParameters.setText(parametersRaw)
    }

    private def initPanel() = {
      val panel = new JPanel()
      panel setLayout new BoxLayout(panel, BoxLayout.Y_AXIS)

      val comp0 = createLabeledElement(ScalaBundle.message("ammonite.script"), createFileBrowser)
      val comp1 = createLabeledElement(ScalaBundle.message("ammonite.executable"), createFileBrowser)
      val comp2 = createLabeledElement(ScalaBundle.message("ammonite.script.parameters"), new RawCommandLineEditor)

      panel.add(comp0, 0)
      panel.add(comp1, 1)
      panel.add(comp2, 2)

      (panel, comp0.getComponent, comp1.getComponent, comp2.getComponent)
    }

    private def createLabeledElement[T <: JComponent](@Nls name: String, comp: T): LabeledComponent[T] = {
      val c = new LabeledComponent[T]
      c.setComponent(comp)
      c.setText(name)
      c
    }

    private def createFileBrowser: TextFieldWithBrowseButton = {
      val fileBrowser = new TextFieldWithBrowseButton()
      fileBrowser.addBrowseFolderListener(ScalaBundle.message("ammonite.config.select"), null, null, new FileChooserDescriptor(true, false, true, true, false, false))
      fileBrowser
    }
  }
}
