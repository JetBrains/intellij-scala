package org.jetbrains.plugins.scala
package lang.psi

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{FileViewProviderFactory, PsiManager}

final class ScFileViewProviderFactory extends FileViewProviderFactory {

  override def createFileViewProvider(file: VirtualFile, language: Language,
                                      manager: PsiManager, eventSystemEnabled: Boolean) =
    new ScFileViewProvider(manager, file, eventSystemEnabled, language)
}
