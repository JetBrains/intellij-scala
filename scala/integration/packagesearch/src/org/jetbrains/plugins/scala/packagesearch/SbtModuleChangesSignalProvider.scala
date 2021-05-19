package org.jetbrains.plugins.scala.packagesearch

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.rd.LifetimeDisposableExKt
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ModuleChangesSignalProvider
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.ISource
import com.jetbrains.rd.util.reactive.Signal
import kotlin.Unit
import org.jetbrains.plugins.scala.project.ProjectExt

class SbtModuleChangesSignalProvider extends ModuleChangesSignalProvider {
  override def listenToModuleChanges(project: Project, lifetime: Lifetime): ISource[kotlin.Unit] = {
    val companion: Signal.Companion = Signal.Companion
    val signal = companion.Void()
    val sbtProjectDataImportListener: ProjectDataImportListener = (s: String) => {
      DumbService.getInstance(project).runWhenSmart(() => signal.fire(Unit.INSTANCE))
    }
    project.getMessageBus.connect(LifetimeDisposableExKt.createNestedDisposable(
      lifetime, 
      "lifetimeToDisposable")).subscribe(
      ProjectDataImportListener.TOPIC,
      sbtProjectDataImportListener
    )
    signal
  }
}
