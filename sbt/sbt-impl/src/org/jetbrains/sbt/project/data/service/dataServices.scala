package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{DataNode, Key}
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.project.external.ScalaAbstractProjectDataService
import org.jetbrains.sbt.project.data.{SbtCommandData, SbtSettingData, SbtTaskData}

import java.util

class SbtTaskDataService extends DefaultDataService[SbtTaskData, Module](SbtTaskData.Key)

class SbtSettingDataService extends DefaultDataService[SbtSettingData, Module](SbtSettingData.Key)

class SbtCommandDataService extends DefaultDataService[SbtCommandData, Module](SbtCommandData.Key)


/**
 * @note data services for external system entities are required for proper serialization (even if they do not contain any logic)
 */
abstract class DefaultDataService[E,I](key: Key[E]) extends ScalaAbstractProjectDataService[E,I](key) {
  override def importData(
    toImport: util.Collection[_ <: DataNode[E]],
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ): Unit = ()
}