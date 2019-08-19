package org.jetbrains.plugins.scala
package worksheet

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{FileViewProvider, FileViewProviderFactory, PsiManager, SingleRootFileViewProvider}

final class WorksheetFileViewProviderFactory extends FileViewProviderFactory {

  override def createFileViewProvider(file: VirtualFile,
                                      language: Language,
                                      manager: PsiManager,
                                      eventSystemEnabled: Boolean): FileViewProvider =
    new WorksheetFileViewProvider(manager, file, eventSystemEnabled)
}

final class WorksheetFileViewProvider(
  manager: PsiManager,
  file: VirtualFile,
  eventSystemEnabled: Boolean
) extends SingleRootFileViewProvider(manager, file, eventSystemEnabled, WorksheetFileType)
