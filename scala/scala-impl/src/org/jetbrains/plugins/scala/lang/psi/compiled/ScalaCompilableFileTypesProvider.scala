package org.jetbrains.plugins.scala.lang.psi.compiled

import java.util

import com.intellij.openapi.compiler.CompilableFileTypesProvider
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.plugins.scala.ScalaFileType

class ScalaCompilableFileTypesProvider extends CompilableFileTypesProvider {
  override def getCompilableFileTypes: util.Set[FileType] = util.Collections.singleton(ScalaFileType.INSTANCE)
}
