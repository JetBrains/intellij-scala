package org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration

import java.io.File
import javax.swing.{BoxLayout, JComponent, JPanel}

import com.intellij.execution.Executor
import com.intellij.execution.configurations._
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.{LabeledComponent, TextFieldWithBrowseButton}
import com.intellij.openapi.util.{JDOMExternalizer, SystemInfo}
import com.intellij.openapi.util.io.FileUtil
import org.jdom.Element
import org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration.AmmoniteRunConfiguration.MyEditor

/**
  * User: Dmitry.Naydanov
  * Date: 12.09.17.
  */
class AmmoniteRunConfiguration(project: Project, factory: ConfigurationFactory) extends 
  RunConfigurationBase(project, factory, AmmoniteRunConfiguration.AMMONITE_RUN_NAME) {
  
  def this(project: Project, factory: ConfigurationFactory, name: String) {
    this(project, factory)
    setFilePath(name)
  }
  
  private var execName: Option[String] = None
  private var fileName: Option[String] = None
  
  def setFilePath(path: String) {
    fileName = Option(path)
  }
  
  def setExecPath(path: String) {
    execName = Option(path)
  }
  
  def getIOFile: Option[File] = fileName.map(new File(_)).filter(_.exists())
  
  override def getConfigurationEditor: SettingsEditor[_ <: RunConfiguration] = new MyEditor

  override def getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = {
    val state = new CommandLineState(environment) {
      override def startProcess(): ProcessHandler = {
        val cmd = new GeneralCommandLine()
        
        execName match {
          case Some(exec) =>
            val exFile = new File(exec)
            if (!exFile.exists()) cmd.setExePath(exec) else {
              cmd.setWorkDirectory(exFile.getParentFile)
              cmd.setExePath("./" + exFile.getName)
            }
          case None => 
            cmd.setExePath("amm")
        }
        
        if (isRepl) {
          cmd.addParameter("--predef")
          cmd.addParameter(s"${createPredefFile(getIOFile.map(_.getName).getOrElse("AmmoniteFile.sc"))}")
        }
        
        fileName.foreach(cmd.addParameter)
        JavaCommandLineStateUtil.startProcess(cmd)
      }
    }
    state.setConsoleBuilder(new TextConsoleBuilderImpl(project))
    state
  }

  override def writeExternal(element: Element) {
    super.writeExternal(element)
    
    def saveFileElement(name: String, maybeString: Option[String]) {
      JDOMExternalizer.write(element, name, maybeString.getOrElse(""))
    }
    
    saveFileElement("execName", execName)
    saveFileElement("fileName", fileName)
  }

  override def readExternal(element: Element) {
    super.readExternal(element)
    
    def retrieveFileElement(name: String): Option[String] = {
      JDOMExternalizer.readString(element, name) match {
        case "" | null => None
        case other => Option(other)
      }
    }
    
    execName = retrieveFileElement("execName")
    fileName = retrieveFileElement("fileName")
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
  val AMMONITE_RUN_NAME = "Ammonite script"
  
  private class MyEditor extends SettingsEditor[AmmoniteRunConfiguration]() {
    private val form = new AmmoniteRunConfigurationForm
    
    override def createEditor(): JComponent = form.panel

    override def applyEditorTo(s: AmmoniteRunConfiguration) {
      val (fileName, execName) = form.get
      s.setFilePath(fileName)
      s.setExecPath(execName)
    }

    override def resetEditorFrom(s: AmmoniteRunConfiguration) {
      form.set(s.fileName.getOrElse(""), s.execName.getOrElse("amm"))
    }
  }
  
  private class AmmoniteRunConfigurationForm {
    val (panel, fileNameField, execNameField) = initPanel()
    
    def get: (String, String) = (fileNameField.getText, execNameField.getText)
    
    def set(fileName: String, execName: String) {
      fileNameField.setText(fileName)
      execNameField.setText(execName)
    }
    
    private def initPanel() = {
      val panel = new JPanel()
      panel setLayout new BoxLayout(panel, BoxLayout.Y_AXIS)
      
      val comp0 = createTextElement("Script:")
      val comp1 = createTextElement("Amm executable:")
      
      panel.add(comp0, 0)
      panel.add(comp1, 1)
      
      (panel, comp0.getComponent, comp1.getComponent)
    }
    
    private def createTextElement(name: String) = {
      val comp = new LabeledComponent[TextFieldWithBrowseButton]()
      
      comp.setComponent(new TextFieldWithBrowseButton())
      comp.setText(name)
      
      comp
    }
  }
}