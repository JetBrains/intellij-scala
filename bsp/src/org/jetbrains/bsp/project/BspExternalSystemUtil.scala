package org.jetbrains.bsp.project

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.bsp.BSP
import org.jetbrains.bsp.data.{BspProjectData, SbtBuildModuleDataBsp, SbtModuleDataBsp}
import org.jetbrains.plugins.scala.util.ExternalSystemUtil

import java.net.URI

object BspExternalSystemUtil {

  def getBspProjectData(project: Project): Option[BspProjectData] = {
    val dataEither = ExternalSystemUtil.getProjectData(BSP.ProjectSystemId, project, BspProjectData.Key)
    dataEither.toSeq.flatten.headOption
  }

  def getSbtModuleData(module: Module): Option[SbtModuleDataBsp] = {
    val project = module.getProject
    val moduleId = ExternalSystemApiUtil.getExternalProjectId(module) // nullable, but that's okay for use in predicate
    getSbtModuleData(project, moduleId)
  }

  def getSbtBuildModuleDataBsp(module: Module): Option[SbtBuildModuleDataBsp] = {
    val project = module.getProject
    val moduleId = ExternalSystemApiUtil.getExternalProjectId(module) // nullable, but that's okay for use in predicate
    getSbtBuildModuleDataBsp(project, moduleId)
  }

  private val EmptyURI = new URI("")

  private def getSbtModuleData(project: Project, moduleId: String): Option[SbtModuleDataBsp] = {
    val moduleDataSeq = getBspModuleData(project, moduleId, SbtModuleDataBsp.Key)
    moduleDataSeq.find(_.id.uri != EmptyURI)
  }

  private def getSbtBuildModuleDataBsp(project: Project, moduleId: String): Option[SbtBuildModuleDataBsp] = {
    val moduleDataSeq = getBspModuleData(project, moduleId, SbtBuildModuleDataBsp.Key)
    moduleDataSeq.find(_.id.uri != EmptyURI)
  }

  private def getBspModuleData[K](project: Project, moduleId: String, key: Key[K]): Iterable[K] = {
    val dataEither = ExternalSystemUtil.getModuleData(BSP.ProjectSystemId, project, moduleId, None, key)
    //TODO: do we need to report the warning to user
    // However there is some code which doesn't expect the data to be present and just checks if it exists
    // So before reporting the warning to user we need to review usage code and decide which code expects
    // the data and which not and then probably split API into two versions: something like "get" and "getOptional"...
    dataEither.getOrElse(Nil)
  }
}
