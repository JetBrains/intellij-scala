package org.jetbrains.bsp

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.{EelProviderUtil, LocalEelDescriptor}
import com.intellij.util.lang.JavaVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.project.external.SdkUtils
import org.jetbrains.sbt.project.SbtProcessJdkGuesser

object BspJdkUtil {

  /**
   * Returns JDK assigned to a project or most recent registered JDK in IDEA. If the first two ways return nothing
   * then try to create and return SDK based on most recent JDK found on the machine.
   */
  @deprecated(message = "Use findOrCreateBestJdkForProject(Option[Project], EelDescriptor)", since = "2026.2")
  @Deprecated(since = "2026.2", forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2026.3")
  def findOrCreateBestJdkForProject(project: Option[Project]): Option[Sdk] =
    project.flatMap { proj => Option(ProjectRootManager.getInstance(proj).getProjectSdk) }
      .orElse(SdkUtils.mostRecentRegisteredJdk)
      .orElse {
        val eelDescriptor = project.map(EelProviderUtil.getEelDescriptor).getOrElse(LocalEelDescriptor.INSTANCE)
        createSdkWithMostRecentFoundJDK(project, eelDescriptor)
      }


  def findOrCreateBestJdkForProject(project: Project): Option[Sdk] =
    findOrCreateBestJdkForProject(Some(project), EelProviderUtil.getEelDescriptor(project))

  /**
   * Returns JDK assigned to a project or most recent registered JDK in IDEA. If the first two ways return nothing
   * then try to create and return SDK based on most recent JDK found on the machine.
   */
  def findOrCreateBestJdkForProject(project: Option[Project], eelDescriptor: EelDescriptor): Option[Sdk] =
    project.flatMap { proj => Option(ProjectRootManager.getInstance(proj).getProjectSdk) }
      .orElse(SdkUtils.mostRecentRegisteredJdk(eelDescriptor))
      .orElse(createSdkWithMostRecentFoundJDK(project, eelDescriptor))

  private def createSdkWithMostRecentFoundJDK(project: Option[Project], eelDescriptor: EelDescriptor): Option[Sdk] = {
    val jdkType = JavaSdk.getInstance

    val detectedJavaHomes: Seq[(String, JavaVersion)] = ProgressManager.getInstance.runProcessWithProgressSynchronously(
      () => SbtProcessJdkGuesser.findAllExistingJavaPaths(jdkType, eelDescriptor),
      BspBundle.message("bsp.import.detecting.jdk"),
      true,
      project.orNull
    )

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
