package org.jetbrains.sbt.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.{ExternalSystemActivityKey, ExternalSystemUtil}
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.sbt.SbtUtil
import org.jetbrains.sbt.project.autolink.SbtUnlinkedProjectAware

private object SbtExternalSystemUtil {

  def loadSbtProject(project: Project): Unit = {
    val externalProjectPath = SbtUtil.getWorkingDirPath(project)
    val extension = new SbtUnlinkedProjectAware()
    if (extension.isLinkedProject(project, externalProjectPath)) {
      // The project was already imported at some point in the past. For the ExternalSystem machinery, it is
      // considered "linked" and can simply be refreshed.
      val builder = new ImportSpecBuilder(project, SbtProjectSystem.Id).use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
      ExternalSystemUtil.refreshProjects(builder)
    } else {
      // The project has never been imported. It needs to be both loaded and linked.
      linkAndLoadSbtProject(extension, project, externalProjectPath)
    }
  }

  /**
   * This code has been translated to Scala from `com.intellij.openapi.externalSystem.autolink.UnlinkedProjectStartupActivity.notifyNotification`.
   *
   * @see [[com.intellij.openapi.externalSystem.autolink.UnlinkedProjectStartupActivity]].
   */
  //noinspection ApiStatus,UnstableApiUsage
  private def linkAndLoadSbtProject(
    extension: SbtUnlinkedProjectAware,
    project: Project,
    externalProjectPath: String
  ): Unit = {
    import com.intellij.openapi.extensions.ExtensionPointUtilKt.createExtensionDisposable
    import com.intellij.openapi.externalSystem.autolink.CoroutineUtilKt.launch
    import com.intellij.platform.backend.observation.TrackingUtil.trackActivity
    import kotlin.coroutines.EmptyCoroutineContext
    import kotlinx.coroutines.CoroutineStart
    // Technically, instances of `com.intellij.openapi.project.Project` should not be passed as instances of
    // `com.intellij.openapi.Disposable`, but this code is copied from the platform, so it is important that it is
    // identical.
    //noinspection IncorrectParentDisposable
    val extensionDisposable = createExtensionDisposable(ExternalSystemUnlinkedProjectAwareProxy.companion().getEP_NAME, extension, project)
    val scope = coroutineScope(project)
    launch(scope, extensionDisposable, EmptyCoroutineContext.INSTANCE, CoroutineStart.DEFAULT, (_, cont1) => {
      trackActivity[kotlin.Unit](project, ExternalSystemActivityKey.INSTANCE, cont2 => {
        extension.linkAndLoadProjectAsync(project, externalProjectPath, cont2)
      }, cont1)
    })
  }

  /**
   * A service created for the only purpose of getting a coroutine scope from the platform.
   *
   * @note Any service can get a coroutine scope injected in its constructor.
   */
  @Service(Array(Service.Level.PROJECT))
  private final class CoroutineScopeService(val coroutineScope: CoroutineScope)

  private def coroutineScope(project: Project): CoroutineScope =
    project.getService(classOf[CoroutineScopeService]).coroutineScope
}
