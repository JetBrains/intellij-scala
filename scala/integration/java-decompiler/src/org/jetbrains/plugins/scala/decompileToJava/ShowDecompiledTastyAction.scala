package org.jetbrains.plugins.scala.decompileToJava

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.fileTypes.{FileType, FileTypeRegistry}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.{PsiTreeUtil, PsiUtilBase}
import com.intellij.psi.{PsiClass, PsiClassOwner, PsiElement}
import org.jetbrains.plugins.scala.decompileToJava.ShowDecompiledTastyAction.*
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.tasty.TastyFileType

/**
 * The action shows the decompiled version of .tasty files in a format of readable Scala code
 *
 * This class was copied from [[org.jetbrains.java.decompiler.ShowDecompiledClassAction]]
 * with the exception that it handles .tasty files instead of .class files
 *
 * @see [[ShowDecompiledClassAsJavaAction]]
 * @see [[org.jetbrains.java.decompiler.ShowDecompiledClassAction]]
 */
class ShowDecompiledTastyAction extends AnAction(ScalaJavaDecompilerBundle.message("show.decompiled.tasty")) {

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  override def update(e: AnActionEvent): Unit = {
    showVisibleAndEnabled(e)
  }

  override def actionPerformed(e: AnActionEvent): Unit = {
    val project = e.getProject
    if (project == null)
      return

    val tastyFile: VirtualFile = getOriginalTastyFile(e).orNull
    if (tastyFile == null)
      return

    PsiNavigationSupport.getInstance().createNavigatable(project, tastyFile, -1).navigate(true)
  }
}

object ShowDecompiledTastyAction {

  private[decompileToJava] def showVisibleAndEnabled(e: AnActionEvent): Unit = {
    val psiElement = getPsiElement(e)

    lazy val originalTastyFile = psiElement.flatMap(getOriginalTastyFile)

    val visible = psiElement.exists(_.getContainingFile.is[PsiClassOwner])
    val enabled = visible && originalTastyFile.isDefined

    e.getPresentation.setVisible(visible)
    e.getPresentation.setEnabled(enabled)
  }

  private[decompileToJava] def getPsiElement(e: AnActionEvent): Option[PsiElement] = {
    val project = e.getProject
    if (project == null) return None

    val editor = e.getData(CommonDataKeys.EDITOR)
    if (editor != null) {
      val file = Option(PsiUtilBase.getPsiFileInEditor(editor, project))
      file.flatMap(file => Option(file.findElementAt(editor.getCaretModel.getOffset)))
    }
    else {
      Option(e.getData(CommonDataKeys.PSI_ELEMENT))
    }
  }

  private[decompileToJava] def getOriginalTastyFile(e: AnActionEvent): Option[VirtualFile] = {
    val psiElement = getPsiElement(e)
    psiElement.flatMap(getOriginalTastyFile)
  }

  private def getOriginalTastyFile(psiElement: PsiElement): Option[VirtualFile] =
    getOriginalFileOfType(psiElement, TastyFileType)

  private def getOriginalFileOfType(psiElement: PsiElement, fileType: FileType): Option[VirtualFile] = {
    val psiClass = PsiTreeUtil.getParentOfType(psiElement, classOf[PsiClass], false)
    val file = Option(psiClass).flatMap(cls => Option(cls.getOriginalElement.getContainingFile.getVirtualFile))
    file.filter(FileTypeRegistry.getInstance.isFileOfType(_, fileType))
  }
}