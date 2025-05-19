package org.jetbrains.sbt.project

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtilKt
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import com.intellij.openapi.progress.CoroutinesKt.runBlockingCancellable
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.annotations.Nullable

object SdkResolutionUtil {

  object DefaultSdkLookupId extends SdkLookupProvider.Id

  def resolveJdkInfo(project: Project, jdkReference: String): SdkInfo = {
    val projectSdk = ProjectRootManager.getInstance(project).getProjectSdk
    resolveJdkInfo(project, projectSdk, jdkReference)
  }

  def resolveJdkInfo(project: Project, @Nullable projectSdk: Sdk, jdkReference: String): SdkInfo =
    runBlockingCancellable((_, continuation) => {
      val provider = getSdkLookupProvider(project)
      ExternalSystemJdkUtilKt.resolveJdkInfo(provider, project, projectSdk, jdkReference, continuation)
    })

  def getSdkLookupProvider(project: Project): SdkLookupProvider =
    SdkLookupProvider.getInstance(project, DefaultSdkLookupId)
}
