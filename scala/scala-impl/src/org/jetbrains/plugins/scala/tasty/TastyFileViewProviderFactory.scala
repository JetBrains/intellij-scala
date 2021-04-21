package org.jetbrains.plugins.scala.tasty

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{FileViewProvider, FileViewProviderFactory, PsiManager}
import org.jetbrains.plugins.scala.Scala3Language
import org.jetbrains.plugins.scala.lang.psi.compiled.ScClassFileDecompiler

class TastyFileViewProviderFactory extends FileViewProviderFactory {
  override def createFileViewProvider(file: VirtualFile, language: Language, manager: PsiManager, eventSystemEnabled: Boolean): FileViewProvider = {
    ScClassFileDecompiler.createFileViewProviderImpl(manager, file, eventSystemEnabled, Scala3Language.INSTANCE)
  }
}