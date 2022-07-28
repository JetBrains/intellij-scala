package org.jetbrains.plugins.scala.worksheet.ammonite.runconfiguration

import com.intellij.execution.RunManagerEx
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.plugins.scala.console.actions.RunConsoleAction
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle
import org.jetbrains.plugins.scala.worksheet.ammonite.AmmoniteUtil

import scala.jdk.CollectionConverters._

class AmmoniteRunScriptAction extends AnAction(WorksheetBundle.message("ammonite.run.script")) {
  def this(target: ScalaFile) = {
    this()
    file = Option(target)
  }
  
  private var file: Option[ScalaFile] = None

  override def actionPerformed(e: AnActionEvent): Unit = {
    file.orElse(Option(e.getData(CommonDataKeys.PSI_FILE))) foreach {
      case ammoniteFile: ScalaFile if AmmoniteUtil.isAmmoniteFile(ammoniteFile) =>
        val project = ammoniteFile.getProject
        val manager = RunManagerEx.getInstanceEx(project)
        val configurationType = ConfigurationTypeUtil.findConfigurationType(classOf[AmmoniteRunConfigurationType])
        val settings = manager.getConfigurationSettingsList(configurationType).asScala
        
        for (setting <- settings) {
          setting.getConfiguration match {
            case ammonite: AmmoniteRunConfiguration =>
              ammonite.getIOFile match {
                case Some(confFile) =>
                  val vFile = ammoniteFile.getVirtualFile
                  if (vFile != null && LocalFileSystem.getInstance().findFileByIoFile(confFile) == vFile) {
                    RunConsoleAction.runExisting(setting, manager, project)
                    return 
                  }
                case _ => 
              }
            case _ => 
          }
        }
        
        RunConsoleAction.createAndRun(configurationType, manager, project, s"Run ${ammoniteFile.getName}", {
          case amm: AmmoniteRunConfiguration =>
            amm.setFilePath(ammoniteFile.getVirtualFile.getCanonicalPath)
          case _ =>
        })
      case _ => 
    }
  }
}
