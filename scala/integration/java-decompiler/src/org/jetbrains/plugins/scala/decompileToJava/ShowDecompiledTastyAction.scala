package org.jetbrains.plugins.scala.decompileToJava

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.{PsiTreeUtil, PsiUtilBase}
import com.intellij.psi.{PsiClass, PsiClassOwner, PsiElement}
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
    val psiElement = getPsiElement(e)
    val visible = psiElement.exists(_.getContainingFile.isInstanceOf[PsiClassOwner])
    val enabled = visible && getOriginalFile(psiElement.orNull) != null
    e.getPresentation.setVisible(visible)
    e.getPresentation.setEnabled(enabled)
  }

  override def actionPerformed(e: AnActionEvent): Unit = {
    val project = e.getProject
    if (project == null) return

    val file = getOriginalFile(getPsiElement(e).orNull)
    if (file == null) return

    PsiNavigationSupport.getInstance().createNavigatable(project, file, -1).navigate(true)
  }

  private def getPsiElement(e: AnActionEvent): Option[PsiElement] = {
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

  private def getOriginalFile(psiElement: PsiElement): VirtualFile = {
    val psiClass = PsiTreeUtil.getParentOfType(psiElement, classOf[PsiClass], false)
    val file = Option(psiClass).flatMap(cls => Option(cls.getOriginalElement.getContainingFile.getVirtualFile))
    file.filter(FileTypeRegistry.getInstance.isFileOfType(_, TastyFileType)).orNull
  }
}