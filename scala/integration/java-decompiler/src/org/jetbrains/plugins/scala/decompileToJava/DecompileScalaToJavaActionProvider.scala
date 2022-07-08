package org.jetbrains.plugins.scala
package decompileToJava

import java.{util => ju}
import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.util.ActionCallback
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.lang.psi.api.ScFile

class DecompileScalaToJavaActionProvider extends AttachSourcesProvider {

  import AttachSourcesProvider.AttachSourcesAction

  override def getActions(list: ju.List[_ <: LibraryOrderEntry],
                          classFile: PsiFile): ju.Collection[AttachSourcesAction] =
    classFile match {
      case file: ScFile if file.isCompiled =>
        val action = new AttachSourcesAction {
          override def getName: String = JavaDecompilerBundle.message("decompile.to.java")

          override def getBusyText: String = JavaDecompilerBundle.message("scala.classfile")

          override def perform(list: ju.List[_ <: LibraryOrderEntry]): ActionCallback = {
            ScalaBytecodeDecompileTask.showDecompiledJavaCode(file)
            ActionCallback.DONE
          }
        }
        ju.Collections.singletonList(action)
      case _ => ju.Collections.emptyList()
    }
}
