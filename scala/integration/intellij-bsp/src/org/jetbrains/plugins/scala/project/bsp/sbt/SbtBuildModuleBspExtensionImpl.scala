package org.jetbrains.plugins.scala.project.bsp.sbt

import ch.epfl.scala.bsp4j.{BuildTargetIdentifier, SbtBuildTarget}
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.{DataNode, ProjectSystemId}
import com.intellij.openapi.module.Module
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil._
import org.jetbrains.plugins.scala.project.bsp.data.{BSP, MyURI, SbtBuildModuleDataBsp}
import org.jetbrains.plugins.bsp.sbt.SbtBuildModuleBspExtension
import org.jetbrains.sbt.project.SbtProjectSystem

class SbtBuildModuleBspExtensionImpl extends SbtBuildModuleBspExtension {
  @Override
  def enrichBspSbtModule(sbtBuildTarget: SbtBuildTarget, baseDirectory: String, systemId: ProjectSystemId, buildTargetId: BuildTargetIdentifier, modelsProvider: IdeModifiableModelsProvider): Unit = {
    println("Hello from SbtBuildModuleBspExtensionImpl.enrichBspSbtModule!")
    val modifiableModuleModel = modelsProvider.getModifiableModuleModel
    println("Hello from SbtBuildModuleBspExtensionImpl.enrichBspSbtModule!")

    val dummySbtBuildModule: Module = modifiableModuleModel.findModuleByName("home.agrodowski.Desktop.ZPP.IJ-HELLO.please-work-helloworld.#root-build") // TODO: get the actual name of the -build module (for now its hardcoded) -> pass it via ext pt?
    println("dummySbtBuildModule: " + dummySbtBuildModule)
    println("dummySbtBuildModule.getProject: " + dummySbtBuildModule.getProject)
    println("systemId: " + systemId)
    println("baseDirectory: " + baseDirectory)

    val externalProjectId = getExternalProjectId(dummySbtBuildModule)
    println("externalProjectId: " + externalProjectId)

    val dummySbtBuildDataNode: DataNode[ModuleData] = findModuleNode(dummySbtBuildModule.getProject, BSP.ProjectSystemId, baseDirectory)
    println("dummySbtBuildDataNode: " + dummySbtBuildDataNode)
    val sbtBuildModuleData = SbtBuildModuleDataBsp(
      id = new MyURI(buildTargetId.getUri),
      imports = sbtBuildTarget.getAutoImports,
      childrenIds = sbtBuildTarget.getChildren.stream().map(child => new MyURI(child.getUri)).collect(java.util.stream.Collectors.toList()),
      sbtVersion = sbtBuildTarget.getSbtVersion
    )
    println("sbtBuildModuleData: " + sbtBuildModuleData)

    val sbtBuildModuleDataNode: DataNode[SbtBuildModuleDataBsp] = new DataNode[SbtBuildModuleDataBsp](SbtBuildModuleDataBsp.Key, sbtBuildModuleData, dummySbtBuildDataNode)
    dummySbtBuildDataNode.addChild(sbtBuildModuleDataNode) // TODO: ask if it will take care of enriching the project structure of sbt module accordingly

    modelsProvider.commit() // TODO: where should the commit be done - in the meeting we have been warned that somehow IJ-BSP overrides changes done to the workspace model
  }
}


