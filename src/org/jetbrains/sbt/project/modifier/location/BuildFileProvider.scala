package org.jetbrains.sbt.project.modifier.location

import java.io.File

import com.intellij.openapi.module.{Module => IJModule}
import com.intellij.openapi.vfs.{VfsUtilCore, VirtualFile, VfsUtil}
import com.intellij.psi.{PsiManager, PsiFile}
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.LocalTimeCounter
import org.jetbrains.sbt.project.modifier.BuildFileElementType
import scala.collection.mutable

/**
 * @author Roman.Shein
 * @since 17.03.2015.
 */
trait BuildFileProvider {
  def findBuildFile(module: IJModule, elementType: BuildFileElementType,
                    vfsFileToCopy: mutable.Map[VirtualFile, LightVirtualFile]): Option[BuildFileEntry[PsiFile]] = {
    //TODO: rewrite this mess
    findIoFile(module, elementType).map { case BuildFileEntry(buildFile, isModuleLocal) =>
      Option(VfsUtil.findFileByIoFile(buildFile, true)).map(originalFile =>
        vfsFileToCopy.getOrElseUpdate(originalFile,
          new LightVirtualFile(originalFile, VfsUtilCore.loadText(originalFile), LocalTimeCounter.currentTime))).
          map(vFile => Option(PsiManager.getInstance(module.getProject).findFile(vFile))).flatten.map(BuildFileEntry
          (_, isModuleLocal))
    }.flatten
  }

  def findIoFile(module: IJModule, elementType: BuildFileElementType): Option[BuildFileEntry[File]]
}

case class BuildFileEntry[T](file: T, isModuleLocal: Boolean)