package org.jetbrains.bsp.project.importing.setup

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ServiceContainerUtil
import org.jetbrains.sbt.project.SbtCachesSetupUtil

/**
 * The test implementation of [[SbtOverBspVmOptions]]. Both the generation of the BSP connection file and the sbt BSP
 * server resolve the dependencies through the JetBrains Maven Central mirror, which avoids HTTP Error 429 Too Many
 * Requests in the CI.
 *
 * The cache options of [[SbtCachesSetupUtil]] are not used, because the tests assert the paths in the default coursier
 * cache.
 */
private final class MirrorSbtOverBspVmOptions extends SbtOverBspVmOptions:
  override def vmOptions: Seq[String] = SbtCachesSetupUtil.repositoryVmOptionsWithBuildReposOverride

object MirrorSbtOverBspVmOptions:
  /** Replaces the [[SbtOverBspVmOptions]] service with [[MirrorSbtOverBspVmOptions]] until `parentDisposable` is disposed */
  def install(parentDisposable: Disposable): Unit =
    ServiceContainerUtil.replaceService(
      ApplicationManager.getApplication,
      classOf[SbtOverBspVmOptions],
      MirrorSbtOverBspVmOptions(),
      parentDisposable
    )
