package org.jetbrains.plugins.scala.decompileToJava

import java.util
import java.util.Collections

import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.util.ActionCallback
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class DecompileScalaToJavaActionProvider extends AttachSourcesProvider {
  override def getActions(
    list:      util.List[LibraryOrderEntry],
    classFile: PsiFile
  ): util.Collection[AttachSourcesProvider.AttachSourcesAction] = classFile match {
    case sfile: ScalaFile if sfile.isCompiled =>
      Collections.singletonList(new AttachSourcesProvider.AttachSourcesAction {
        override def getName: String     = "Decompile to Java"
        override def getBusyText: String = "Scala Classfile"

        override def perform(list: util.List[LibraryOrderEntry]): ActionCallback = {
          ScalaBytecodeDecompileTask.showDecompiledJavaCode(sfile)
          ActionCallback.DONE
        }
      })
    case _ => Collections.emptyList()
  }
}
