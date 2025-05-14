package org.jetbrains.bsp

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.projectRoots.{JavaSdk, Sdk}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.scala.project.external.SdkUtils
import org.jetbrains.sbt.project.SbtProcessJdkGuesser


object BspJdkUtil {

  /**
    Returns JDK assigned to a project or most recent registered JDK in IDEA. If the first two ways return nothing
   then try to create and return SDK based on most recent JDK found on the machine.
   */
  def findOrCreateBestJdkForProject(project: Option[Project]): Option[Sdk] =
    project.flatMap { proj => Option(ProjectRootManager.getInstance(proj).getProjectSdk) }
      .orElse(SdkUtils.mostRecentRegisteredJdk)
      .orElse(createSdkWithMostRecentFoundJDK)

  private def createSdkWithMostRecentFoundJDK: Option[Sdk] = {
    val jdkType = JavaSdk.getInstance

    val detectedJavaHomes: Seq[(String, JavaVersion)] = ProgressManager.getInstance.runProcessWithProgressSynchronously(
      () => SbtProcessJdkGuesser.findAllExistingJavaPaths(jdkType),
      BspBundle.message("bsp.import.detecting.jdk"),
      true,
      null
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
