package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.{FileEditor, FileEditorManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.JavaProjectRootsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiJavaFile, PsiManager}
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.plugins.scala.compiler.ScalaCompileServerSettings
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.{NullSafe, PsiFileExt, ToNullSafe, inReadAction}
import org.jetbrains.plugins.scala.externalHighlighters.compiler.DocumentCompiler
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.VirtualFileExt

import java.nio.file.{Path, Paths}
import scala.concurrent.{ExecutionContext, Future}

@Service
final class TriggerCompilerHighlightingService(project: Project)
  extends Disposable {

  private var alreadyHighlighted = Set.empty[Path]
  private var changedFiles = Set.empty[VirtualFile]

  private val threadPool = AppExecutorUtil.createBoundedApplicationPoolExecutor("TriggerCompilerHighlighting", 1)
  private implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(threadPool)

  def triggerOnFileChange(psiFile: PsiFile, virtualFile: VirtualFile): Unit =
    if (isHighlightingEnabled && isHighlightingEnabledFor(psiFile, virtualFile))
      virtualFile.findDocument.foreach { document =>
        if (psiFile.isScalaWorksheet) {
          triggerWorksheetCompilation(psiFile, document, markHighlighted = None)
        } else asyncAtomic {
          val changedFilesSizeBefore = changedFiles.size
          changedFiles += virtualFile
          if (changedFiles.size > 1 && changedFiles.size != changedFilesSizeBefore)
            triggerIncrementalCompilation()
          else
            triggerDocumentCompilation(document, psiFile, markHighlighted = None)
        }
      }

  def triggerOnSelectionChange(editor: FileEditor): Unit =
    if (isHighlightingEnabled)
      for {
        virtualFile <- editor.getFile.nullSafe
        psiFile <- inReadAction(PsiManager.getInstance(project).findFile(virtualFile)).nullSafe
        if isHighlightingEnabledFor(psiFile, virtualFile)
        pathString <- virtualFile.getCanonicalPath.nullSafe
        document <- inReadAction(virtualFile.findDocument)
      } asyncAtomic {
        val path = Paths.get(pathString)
        if (changedFiles.nonEmpty)
          triggerIncrementalCompilation()
        else if (!alreadyHighlighted.contains(path))
          if (psiFile.isScalaWorksheet)
            triggerWorksheetCompilation(psiFile, document, markHighlighted = Some(path))
          else
            triggerDocumentCompilation(document, psiFile, markHighlighted = Some(path))
      }

  override def dispose(): Unit = {
    synchronized {
      changedFiles = Set.empty
      alreadyHighlighted = Set.empty
    }
    threadPool.shutdownNow()
  }

  private def isHighlightingEnabled: Boolean =
    !PowerSaveMode.isEnabled &&
      ScalaCompileServerSettings.getInstance.COMPILE_SERVER_ENABLED &&
      ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)

  private def isHighlightingEnabledFor(psiFile: PsiFile, virtualFile: VirtualFile): Boolean = {
    lazy val isJavaOrScalaFile = psiFile match {
      case _ if psiFile.isScalaWorksheet => true
      case _: ScalaFile | _: PsiJavaFile if !JavaProjectRootsUtil.isOutsideJavaSourceRoot(psiFile) => true
      case _ => false
    }
    virtualFile.isInLocalFileSystem && isJavaOrScalaFile
  }

  private def triggerIncrementalCompilation(): Unit = {
    def saveChangedDocuments(): Unit = synchronized {
      changedFiles
        .flatMap { virtualFile => inReadAction(virtualFile.findDocument) }
        .foreach(_.syncToDisk(project))
      changedFiles = Set.empty
    }

    def eraseAlreadyHighlighted(): Unit = synchronized {
      alreadyHighlighted = Set.empty
    }

    def triggerSelectedDocumentCompilation(): Unit =
      FileEditorManager.getInstance(project).getSelectedEditor.nullSafe
        .foreach(triggerOnSelectionChange)
        
    def clearDocumentCompilerOutputDirectories(): Unit =
      DocumentCompiler.get(project).clearOutputDirectories()

    val psiManager = PsiManager.getInstance(project)
    val showErrorsFromCompilerEnabledAtLeastForOneOpenedFile =
      FileEditorManager.getInstance(project).getAllEditors.exists { editor =>
        val isShowErrorsFromCompilerEnabled = for {
          virtualFile <- Option(editor.getFile)
          psiFile <- Option(inReadAction(psiManager.findFile(virtualFile)))
        } yield ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiFile)
        isShowErrorsFromCompilerEnabled.exists(identity)
      }

    if (showErrorsFromCompilerEnabledAtLeastForOneOpenedFile) // SCL-18946
      CompilerHighlightingService.get(project).triggerIncrementalCompilation(
        beforeCompilation = { () =>
          saveChangedDocuments()
        },
        afterCompilation = { () =>
          eraseAlreadyHighlighted()
          clearDocumentCompilerOutputDirectories()
          triggerSelectedDocumentCompilation()
        }
      )
  }

  private def triggerDocumentCompilation(document: Document,
                                         psiFile: PsiFile,
                                         markHighlighted: Option[Path]): Unit =
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(psiFile))
      CompilerHighlightingService.get(project).triggerDocumentCompilation(
        document = document,
        afterCompilation = () => mark(markHighlighted)
      )

  private def triggerWorksheetCompilation(psiFile: PsiFile,
                                          document: Document,
                                          markHighlighted: Option[Path]): Unit =
    CompilerHighlightingService.get(project).triggerWorksheetCompilation(
      psiFile = psiFile,
      document = document,
      afterCompilation = () => mark(markHighlighted)
    )

  private def mark(pathOption: Option[Path]): Unit = pathOption.foreach { path =>
    synchronized {
      alreadyHighlighted += path
    }
  }

  private def asyncAtomic(action: => Unit): Unit =
    Future(synchronized(action))
}

object TriggerCompilerHighlightingService {

  def get(project: Project): TriggerCompilerHighlightingService =
    project.getService(classOf[TriggerCompilerHighlightingService])
}
