package org.jetbrains.plugins.scala.worksheet.actions

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.scala.project.{UserDataHolderExt, UserDataKeys}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

import scala.collection.mutable
import scala.ref.Reference

@Service
final class WorksheetSyntheticModuleService(project: Project) {

  private val modulesMap = mutable.HashMap[VirtualFile, WorksheetSyntheticModule]()

  private def psiManager = PsiManager.getInstance(project)

  def moduleUpdated(virtualFile: VirtualFile): Unit =
    attachWorksheetModuleToPsiFile(virtualFile)

  def ensureWorksheetModuleAttachedToPsiFile(virtualFile: VirtualFile): Unit = {
    val psiFile = psiManager.findFile(virtualFile)
    if (psiFile != null) {
      attachWorksheetModuleToPsiFile(virtualFile, psiFile)
    }
  }

  private def attachWorksheetModuleToPsiFile(virtualFile: VirtualFile): Unit = {
    val psiFile = psiManager.findFile(virtualFile)
    if (psiFile != null) {
      attachWorksheetModuleToPsiFile(virtualFile, psiFile)
    }
  }

  private def attachWorksheetModuleToPsiFile(virtualFile: VirtualFile, psiFile: PsiFile): Unit =
    for {
      wrapperModule <- syntheticModuleForFile(virtualFile)
    } {
      val moduleReferenceRef = moduleReference(wrapperModule)
      psiFile.putUserData(UserDataKeys.SCALA_ATTACHED_MODULE, moduleReferenceRef)
    }

  private def syntheticModuleForFile(virtualFile: VirtualFile): Option[WorksheetSyntheticModule] = {
    val cpModule = WorksheetFileSettings(project, virtualFile).getModule
    cpModule.map(syntheticModuleForFile(virtualFile, _))
  }

  private def syntheticModuleForFile(virtualFile: VirtualFile, currentCpModule: Module): WorksheetSyntheticModule = {
    val maybeCached = modulesMap.get(virtualFile)
    val result = maybeCached match {
      case Some(cached) =>
        if (cached.cpModule != currentCpModule) {
          Disposer.dispose(cached)
          registerNewSyntheticModuleForFile(virtualFile, currentCpModule)
        } else
          cached
      case _ =>
        registerNewSyntheticModuleForFile(virtualFile, currentCpModule)
    }
    result
  }

  private def registerNewSyntheticModuleForFile(virtualFile: VirtualFile, currentCpModule: Module): WorksheetSyntheticModule = {
    val module = new WorksheetSyntheticModule(virtualFile, currentCpModule)
    Disposer.register(currentCpModule, module)
    modulesMap.put(virtualFile, module)
    module
  }

  private def moduleReference(module: Module): Reference[Module] =
    new Reference[Module] {
      override def apply(): Module = module
      override def get: Option[Module] = Some(apply())
      override def clear(): Unit = ()
      override def enqueue(): Boolean = false
      override def isEnqueued: Boolean = false
    }
}

object WorksheetSyntheticModuleService {

  def apply(project: Project): WorksheetSyntheticModuleService =
    project.getService(classOf[WorksheetSyntheticModuleService])
}
