package org.jetbrains.plugins.cbt.project.model

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import org.jetbrains.plugins.cbt.project.model.CbtProjectInfo._
import org.jetbrains.plugins.cbt.project.settings.CbtExecutionSettings

class CbtSourceProjectConverter(project: Project, settings: CbtExecutionSettings)
  extends CbtProjectConverter(project, settings) {
  override private[model] val librariesMap = project.libraries.map(l => l.name -> l).toMap

  override private[model] def createCbtDependencies(moduleNode: DataNode[ModuleData]) = Seq.empty

  override private[model] def createModule(parent: DataNode[_])(module: CbtProjectInfo.Module) =
    (super.createModule(parent) _)
      .andThen(addModuleDependencies(module))(module)

  private def addModuleDependencies(module: Module)(moduleNode: DataNode[ModuleData]) = {
    val name = module.name
    project.modules
      .filter { m =>
        m.name != name &&
          m.name.contains("libraries") &&
          !m.name.contains("build")
      }
      .map(m => ModuleDependency(m.name))
      .map(createModuleDependency(moduleNode))
      .foreach(moduleNode.addChild)

    if (name != "cbt") {
      modulesMap.get("cbt")
        .map(m => ModuleDependency(m.name))
        .map(createModuleDependency(moduleNode))
        .foreach(moduleNode.addChild)
    }
    moduleNode
  }

  override private[model] def convertProject(project: Project) =
    (super.convertProject _)
      .andThen(addCbtLibraries _)(project)

  private def addCbtLibraries(projectNode: DataNode[ProjectData]) = {
    project.cbtLibraries
      .map(createLibraryNode(projectNode))
      .foreach(projectNode.addChild)
    projectNode
  }
}
