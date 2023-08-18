package org.jetbrains.plugins.scala.project.external

import com.intellij.openapi.externalSystem.model.{DataNode, ProjectKeys}
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module


trait IdeModifiableModelsProviderImplicitConversions {

  protected implicit class IdeModifiableModelsProviderOps(private val modelsProvider: IdeModifiableModelsProvider) {

    def findIdeModuleOpt(name: String): Option[Module] =
      Option(modelsProvider.findIdeModule(name))

    def findIdeModuleOpt(data: ModuleData): Option[Module] =
      Option(modelsProvider.findIdeModule(data))

    def getIdeModuleByNode(node: DataNode[_]): Option[Module] =
      for {
        moduleData <- Option(node.getData(ProjectKeys.MODULE))
        module <- findIdeModuleOpt(moduleData)
      } yield module
  }

}
