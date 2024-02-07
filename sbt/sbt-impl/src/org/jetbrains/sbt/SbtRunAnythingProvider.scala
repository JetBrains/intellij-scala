package org.jetbrains.sbt

import com.intellij.ide.actions.runAnything.RunAnythingUtil._
import com.intellij.ide.actions.runAnything.activity.RunAnythingProviderBase
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.sbt.SbtRunAnythingProvider._
import org.jetbrains.sbt.icons.Icons
import org.jetbrains.sbt.project.data.{SbtSettingData, SbtTaskData}
import org.jetbrains.sbt.settings.SbtSettings
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.jetbrains.sbt.shell.action.SbtNodeAction

import java.util
import javax.swing.Icon
import scala.jdk.CollectionConverters._

class SbtRunAnythingProvider extends RunAnythingProviderBase[SbtRunItem] {

  override def getValues(dataContext: DataContext, pattern: String): util.Collection[SbtRunItem] = {

    val values = if (canRunSbt(dataContext, pattern)) {
      val project = fetchProject(dataContext)
      val queryString = Option(StringUtil.substringAfter(pattern, " ")).getOrElse("")
      val projectCommandString = StringUtil.split(queryString, "/").asScala.toList.map(_.trim)

      // when entering command in form project/command, suggest commands in scope of project
      val (projectString, commandString) = projectCommandString match {
        case Nil => ("", "")
        case command :: Nil => ("", command)
        case projectId :: command :: _ => (projectId, command)
      }

      val modules = ModuleManager.getInstance(project).getModules.toList
      val moduleDataAndKeys = modules.flatMap { module =>
        val maybeModuleData = SbtUtil.getSbtModuleData(project, module)

        // suggest settings and tasks scoped to project
        maybeModuleData.flatMap { moduleData =>
          val moduleTasks = SbtUtil.getSbtModuleData(project, module, SbtTaskData.Key).toList
          val moduleSettings = SbtUtil.getSbtModuleData(project, module, SbtSettingData.Key).toList
          val moduleKeys = moduleSettings ++ moduleTasks
          val relevantEntries = moduleKeys.filter { td => td.name.contains(commandString) }

          if (moduleData.id.contains(projectString))
            Some((moduleData, relevantEntries))
          else None
        }
      }

      // TODO suggest command completions, but only for root project (assumption is that is where the shell is usually)

      val suggestions = moduleDataAndKeys.flatMap { case (moduleData, keys) =>
        val projectId = SbtUtil.makeSbtProjectId(moduleData)
        val basicSuggestion = SbtShellTask(projectId, commandString)
        val taskSuggestions = keys.sortBy(_.rank).map { key => SbtShellTask(projectId, key.name) }
        basicSuggestion :: taskSuggestions
      }

      val defaultSuggestion = SbtShellCommandString(commandString)
      defaultSuggestion :: suggestions
    } else Nil

    (values: List[SbtRunItem]).asJava
  }

  override def execute(dataContext: DataContext, value: SbtRunItem): Unit = {
    val project = fetchProject(dataContext)
    val com = SbtShellCommunication.forProject(project)
    com.command(value.command)
  }

  override def getCommand(value: SbtRunItem): String = {
    "sbt " + value.command
  }

  override def findMatchingValue(dataContext: DataContext, pattern: String): SbtRunItem = {
    if (canRunSbt(dataContext, pattern)) {
      val commandString = StringUtil.substringAfter(pattern, " ")
      SbtShellCommandString(commandString)
    } else null
  }

  override def getIcon(value: SbtRunItem): Icon = Icons.SBT_SHELL

  override def getCompletionGroupTitle: String = "sbt"

  private def canRunSbt(dataContext: DataContext, pattern: String) = {
    if (pattern.startsWith("sbt")) {
      val project = fetchProject(dataContext)
      val sbtSettings = SbtSettings.getInstance(project).getLinkedProjectsSettings
      !sbtSettings.isEmpty
    } else false
  }

}

object SbtRunAnythingProvider {
  sealed abstract class SbtRunItem {
    def command: String
  }
  case class SbtShellCommandString(override val command: String) extends SbtRunItem
  case class SbtShellTask(projectId: String, task: String) extends SbtRunItem {
    override def command: String = SbtNodeAction.scopedKey(projectId, task)
  }
}

