package org.jetbrains.mill
package language

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{FileViewProviderFactory, PsiManager, SingleRootFileViewProvider}

final class MillFileViewProviderFactory extends FileViewProviderFactory {

  override def createFileViewProvider(file: VirtualFile,
                                      language: Language,
                                      manager: PsiManager,
                                      eventSystemEnabled: Boolean): SingleRootFileViewProvider =
    new SingleRootFileViewProvider(manager, file, eventSystemEnabled, language) {}
}
