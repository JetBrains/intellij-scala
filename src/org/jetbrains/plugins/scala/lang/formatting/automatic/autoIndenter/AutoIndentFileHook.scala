package org.jetbrains.plugins.scala
package lang.formatting.automatic.autoIndenter

import com.intellij.openapi.project.Project
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.fileEditor.{FileEditorManagerEvent, FileEditorManager, FileEditorManagerListener}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.lang.formatting.ScalaBlock
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.formatting.Indent

/**
 * Created by Roman.Shein on 18.08.2014.
 */
class AutoIndentFileHook(private val project: Project) extends ProjectComponent {
  override def disposeComponent() {}

  override def initComponent() {}

  override def projectClosed() {
    //TODO: maybe put something here
  }

  override def projectOpened() {
    project.getMessageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, AutoIndentEditorListener)
  }

  override def getComponentName: String = "Launch autoIndenter matcher on file opened"

  private object AutoIndentEditorListener extends FileEditorManagerListener {
    override def selectionChanged(event: FileEditorManagerEvent) {}

    override def fileClosed(source: FileEditorManager, file: VirtualFile) {}

    private def generateBlocks(root: ScalaBlock) {
      import scala.collection.JavaConversions._
      root.getSubBlocks().toList.map(block => generateBlocks(block.asInstanceOf[ScalaBlock]))
    }

    override def fileOpened(source: FileEditorManager, file: VirtualFile) {
      println("running autoindenter preparations for file " + file.getName)
      val startTime = System.currentTimeMillis()
      val psiFile = PsiManager.getInstance(project).findFile(file)
      val astNode = psiFile.getNode
      val codeStyleSettings = new CodeStyleSettings
      val topBlock = new ScalaBlock(null, astNode, null, null, Indent.getAbsoluteNoneIndent, null, codeStyleSettings)
      generateBlocks(topBlock)
      AutoIndenter.prepareAutoIndenter(topBlock, project, file)
      println("time consumed = " + (System.currentTimeMillis() - startTime))
    }
  }
}