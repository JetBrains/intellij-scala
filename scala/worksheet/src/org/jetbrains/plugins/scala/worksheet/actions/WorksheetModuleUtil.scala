package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SlowOperations
import org.jetbrains.plugins.scala.project.{ModuleExt, UserDataKeys}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

import scala.ref.WeakReference
import scala.util.Using

private object WorksheetModuleUtil {

  def ensureModuleAttached(project: Project, virtualFile: VirtualFile): Unit = {
    for {
      wrapperModule <- moduleForFile(project, virtualFile)
    } {
      val moduleReferenceRef = WeakReference(wrapperModule)
      virtualFile.putUserData(UserDataKeys.SCALA_ATTACHED_MODULE, moduleReferenceRef)
    }
  }

  private def moduleForFile(project: Project, virtualFile: VirtualFile): Option[Module] = {
    //noinspection ApiStatus
    val cpModule = Using.resource(SlowOperations.knownIssue("SCL-22095, SCL-22097")) { _ =>
      WorksheetFileSettings(project, virtualFile).getModule
    }
    cpModule.map(_.findRepresentativeModuleForSharedSourceModuleOrSelf)
  }
}
