package org.jetbrains.plugins.scala
package lang.psi

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.{FileType, PlainTextLanguage}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager, SingleRootFileViewProvider}
import com.intellij.testFramework.LightVirtualFile

/**
 * For the decompiled-files see [[org.jetbrains.plugins.scala.lang.psi.compiled.ScClsFileViewProvider]]
 */
final class ScFileViewProvider(
  manager: PsiManager,
  file: VirtualFile,
  eventSystemEnabled: Boolean,
  language: Language
) extends SingleRootFileViewProvider(
  manager,
  file,
  eventSystemEnabled,
  ScFileViewProvider.calcBaseLanguage(file, language)
) {

  override def createFile(
    project: Project,
    file: VirtualFile,
    fileType: FileType
  ): PsiFile =
    createFile(getBaseLanguage)

  override def createCopy(copy: VirtualFile) =
    new ScFileViewProvider(getManager, copy, eventSystemEnabled = false, getBaseLanguage)
}

object ScFileViewProvider {

  /**
   * This is a partial copy of the method [[com.intellij.psi.SingleRootFileViewProvider#calcBaseLanguage]]
   *
   * I haven't copied the full logic, only the part required for SCL-24093
   *
   * @see [[com.intellij.psi.impl.PsiFileFactoryImpl#trySetupPsiForFile]]
   */
  def calcBaseLanguage(file: VirtualFile, language: Language): Language = {
    if (SingleRootFileViewProvider.isTooLargeForIntelligence(file)) {
      // Treat too large files from sources as plain text, disable highlighting and any code insight.
      // This is done for performance reasons.
      // However, don't do it for the decompiled class files.
      // NOTE: It's the same as in Java - they also don't do it for decompiled java class files, but they do it for .java source files
      val isDecompiledScalaClassFile = isInMemoryFileWithOriginalJvmClassFile(file)
      if (!isDecompiledScalaClassFile)
        PlainTextLanguage.INSTANCE
      else
        language
    }
    else language
  }

  private def isInMemoryFileWithOriginalJvmClassFile(file: VirtualFile): Boolean = file match {
    case light: LightVirtualFile =>
      val originalFile = light.getOriginalFile
      originalFile != null && originalFile.getFileType == JavaClassFileType.INSTANCE
    case _ =>
      false
  }
}
