package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.project.wizard.AbstractExternalModuleBuilder
import org.jetbrains.sbt.project.settings.SbtProjectSettings
import com.intellij.openapi.module.{Module, ModifiableModuleModel, ModuleType}
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import com.intellij.openapi.util.io.FileUtil
import javax.swing.Icon

/**
 * User: Dmitry Naydanov
 * Date: 11/23/13
 */
class SbtModuleBuilder extends AbstractExternalModuleBuilder[SbtProjectSettings](SbtProjectSystem.Id, new SbtProjectSettings) {
  def getExternalProjectConfigFile(contentRootDir: VirtualFile): VirtualFile = null

  def getTemplateConfigName(settings: SbtProjectSettings): String = null

  def getModuleType: ModuleType[_ <: ModuleBuilder] = SbtModuleType.instance

  override def createModule(moduleModel: ModifiableModuleModel): Module = {
    createSbtStub()
    super.createModule(moduleModel)
  }
  
  private def createSbtStub() {
    val modulePath = getModuleFileDirectory
    val moduleName = getName

    val dir = new File(modulePath)

    if (!dir.exists() || !dir.isDirectory) return
    
    val buildFile = new File(dir, "build.sbt")
    val projectDir = new File(dir, "project")
    val pluginsFile = new File(projectDir, "plugins.sbt")

    if (!buildFile.createNewFile() || !FileUtil.createDirectory(projectDir) || !pluginsFile.createNewFile()) return

    FileUtil.writeToFile(buildFile, SbtModuleBuilder buildSbt moduleName)
    FileUtil.writeToFile(pluginsFile, SbtModuleBuilder.pluginsSbt)
  }

  override def getNodeIcon: Icon = Sbt.Icon
}

object SbtModuleBuilder {
  def buildSbt(name: String) =
    s"""name := "$name"
      |
      |version := "1.0"
    """.stripMargin
  
  def pluginsSbt = "logLevel := Level.Warn"
}