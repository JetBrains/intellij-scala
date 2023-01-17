package org.jetbrains.plugins.scala
package decompileToJava

import com.intellij.codeInsight.daemon.impl.analysis.{FileHighlightingSetting, HighlightLevelUtil}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.{FileDocumentManager, OpenFileDescriptor}
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile, VirtualFileManager}
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.lang.psi.api.ScFile

class ScalaBytecodeDecompileTask(file: ScFile)
    extends Task.Backgroundable(file.getProject, JavaDecompilerBundle.message("decompile.scala.bytecode")) {

  import ScalaBytecodeDecompileTask._

  override def run(indicator: ProgressIndicator): Unit = {
    indicator.setText(JavaDecompilerBundle.message("decompiling.file", file.getName))

    val tryDecompile = ScalaDecompilerService().decompile(file)

    ApplicationManager.getApplication.invokeLater { () =>
      inWriteAction {
        if (file.isValid && !file.getProject.isDisposed) {
          tryDecompile.fold(
            e => Messages.showErrorDialog(
              JavaDecompilerBundle.message("cannot.decompile.filename.colon.message", file.getName, e.getMessage),
              JavaDecompilerBundle.message("decompiler.error")
            ),
            text => {
              val root           = getOrCreateDummyRoot()
              val decompiledName = FileUtil.getNameWithoutExtension(file.getName) + ".decompiled.java"
              val result         = DummyFileSystem.getInstance().createChildFile(null, root, decompiledName)
              PsiManager.getInstance(myProject).findFile(result) match {
                case null =>
                case psiFile => HighlightLevelUtil.forceRootHighlighting(psiFile, FileHighlightingSetting.SKIP_HIGHLIGHTING)
              }
              val document = FileDocumentManager.getInstance().getDocument(result)
              if (document != null) {
                document.setText(text)
              }
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
      .refreshAndFindFileByUrl(scalaDecompiledRoot) match {
      case null => DummyFileSystem.getInstance().createRoot(scalaDecompiledFolder)
      case url => url
    }

  def showDecompiledJavaCode(file: ScFile): Unit =
    ProgressManager.getInstance().run(new ScalaBytecodeDecompileTask(file))
}
