package org.jetbrains.bsp

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.{JavaHomeFinder, SdkConfigurationUtil}
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.scala.project.external.SdkUtils

import scala.jdk.CollectionConverters.CollectionHasAsScala

object BspJdkUtil {

  /**
    Returns JDK assigned to a project or most recent registered JDK in IDEA. If the first two ways return nothing
   then try to create and return SDK based on most recent JDK found on the machine.
   */
  def getMostSuitableJdkForProject(project: Option[Project]): Option[Sdk] =
    project.flatMap { proj => Option(ProjectRootManager.getInstance(proj).getProjectSdk) }
      .orElse(SdkUtils.mostRecentRegisteredJdk)
      .orElse(createSdkWithMostRecentFoundJDK)

  private def createSdkWithMostRecentFoundJDK: Option[Sdk] = {
    //return None
    val jdkType = JavaSdk.getInstance

    val detectedJavaHomes: Seq[(String, JavaVersion)] = JavaHomeFinder.suggestHomePaths(false).asScala.toSeq
      .filter(jdkType.isValidSdkHome)
      .map(p => (p, JavaVersion.tryParse(p)))
      .filter(t => t._2 != null)

    val latestJavaHome: Option[String] = detectedJavaHomes
      .maxByOption(_._2)
      .map(_._1)

    latestJavaHome.map { home =>
      ExternalSystemApiUtil.executeOnEdt(() =>
        SdkConfigurationUtil.createAndAddSDK(home, jdkType)
      )
    }
  }
}
