package org.jetbrains.sbt.language

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiManager, SingleRootFileViewProvider}

private final class SbtFileViewProvider(
  manager: PsiManager,
  file: VirtualFile,
  eventSystemEnabled: Boolean,
  language: Language
) extends SingleRootFileViewProvider(manager, file, eventSystemEnabled, language)
