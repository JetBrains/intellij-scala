package org.jetbrains.plugins.scala.actions.decompileToJava

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile, VirtualFileManager}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ScalaBytecodeDecompileTask(file: ScalaFile)
    extends Task.Backgroundable(file.getProject, "Decompile Scala Bytecode") {
  import ScalaBytecodeDecompileTask._

  override def run(indicator: ProgressIndicator): Unit = {
    indicator.setText(s"Decompiling ${file.name}")

    val tryDecompile = ScalaDecompilerService().decompile(file)

    ApplicationManager.getApplication.invokeLater { () =>
      inWriteAction {
        if (file.isValid && !file.getProject.isDisposed) {
          tryDecompile.fold(
            e => Messages.showErrorDialog(s"Cannot decompile ${file.name}: ${e.message}", "Decompiler Error"),
            text => {
              val root           = getOrCreateDummyRoot()
              val decompiledName = FileUtil.getNameWithoutExtension(file.name) + ".decompiled.java"
              val result         = DummyFileSystem.getInstance().createChildFile(null, root, decompiledName)
              VfsUtil.saveText(result, text)
              new OpenFileDescriptor(file.getProject, result).navigate(true)
            }
          )
        }
      }
    }
  }
}

object ScalaBytecodeDecompileTask {
  private[decompileToJava] val scalaDecompiledFolder = "scala-decompiled"
  private[decompileToJava] val scalaDecompiledRoot   = s"dummy://$scalaDecompiledFolder"

  private def getOrCreateDummyRoot(): VirtualFile =
    VirtualFileManager
      .getInstance()
      .refreshAndFindFileByUrl(scalaDecompiledRoot)
      .toOption
      .getOrElse(DummyFileSystem.getInstance().createRoot(scalaDecompiledFolder))

  def showDecompiledJavaCode(file: ScalaFile): Unit =
    ProgressManager.getInstance().run(new ScalaBytecodeDecompileTask(file))
}
