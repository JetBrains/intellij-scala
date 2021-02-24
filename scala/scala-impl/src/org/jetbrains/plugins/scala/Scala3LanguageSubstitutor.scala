package org.jetbrains.plugins.scala

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.LanguageSubstitutor
import org.jetbrains.plugins.scala.project.ProjectExt

class Scala3LanguageSubstitutor extends LanguageSubstitutor {
  /**
   * This will set the language of a scala file to scala 3
   */
  override def getLanguage(file: VirtualFile, project: Project): Language =
    if (file.getFileType == ScalaFileType.INSTANCE && project.hasScala3) Scala3Language.INSTANCE
    else null
}
