package org.jetbrains.plugins.scala
package worksheet

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{FileViewProviderFactory, PsiManager, SingleRootFileViewProvider}

final class WorksheetFileViewProviderFactory extends FileViewProviderFactory {

  override def createFileViewProvider(
    file: VirtualFile,
    language: Language,
    manager: PsiManager,
    eventSystemEnabled: Boolean
  ): SingleRootFileViewProvider =
    new WorksheetFileViewProvider(manager, file, eventSystemEnabled, language)
}

private final class WorksheetFileViewProvider(
  manager: PsiManager,
  file: VirtualFile,
  eventSystemEnabled: Boolean,
  language: Language,
) extends SingleRootFileViewProvider(
  manager,
  file,
  eventSystemEnabled,
  language
)