package org.jetbrains.sbt.project.modifier.location

import com.intellij.openapi.module.{Module => IJModule}
import com.intellij.openapi.vfs.{VfsUtil, VfsUtilCore, VirtualFile}
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.LocalTimeCounter
import org.jetbrains.sbt.project.modifier.BuildFileElementType

import java.io.File
import scala.collection.mutable

trait BuildFileProvider {
  def findBuildFile(module: IJModule, elementType: BuildFileElementType,
                    vfsFileToCopy: mutable.Map[VirtualFile, LightVirtualFile]): Option[BuildFileEntry[PsiFile]] = {

    def findVirtualFile(file: File) = Option(VfsUtil.findFileByIoFile(file, true))

    def toLightVirtualFile(origFile: VirtualFile) = vfsFileToCopy.getOrElseUpdate(origFile,
      new LightVirtualFile(origFile, VfsUtilCore.loadText(origFile), LocalTimeCounter.currentTime))

    def toPsiFile(vFile: VirtualFile) = Option(PsiManager.getInstance(module.getProject).findFile(vFile))

    findIoFile(module, elementType).flatMap {
      case BuildFileEntry(buildFile, isModuleLocal) =>
        findVirtualFile(buildFile)
          .map(toLightVirtualFile)
          .flatMap(toPsiFile)
          .map(BuildFileEntry(_, isModuleLocal))
    }
  }


  def findIoFile(module: IJModule, elementType: BuildFileElementType): Option[BuildFileEntry[File]]
}

case class BuildFileEntry[T](file: T, isModuleLocal: Boolean)