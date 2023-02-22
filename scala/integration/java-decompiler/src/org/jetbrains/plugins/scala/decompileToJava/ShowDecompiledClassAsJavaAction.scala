package org.jetbrains.plugins.scala.decompileToJava

import com.intellij.openapi.actionSystem.{ActionUpdateThread, AnAction, AnActionEvent, CommonDataKeys}
import com.intellij.openapi.project.DumbService
import com.intellij.psi.impl.JavaPsiImplementationHelper
import com.intellij.psi.util.{CachedValueProvider, CachedValuesManager, PsiModificationTracker, PsiTreeUtil, PsiUtilBase}
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.ScFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * Shows a decompiled class file in Java format.
 *
 * It's similar to the platform action [[org.jetbrains.java.decompiler.ShowDecompiledClassAction]].
 *
 * The difference is that `ShowDecompiledClassAction` opens a decompiled `.class` file which has Scala syntax.
 * (It doesn't decompile method bodies, it only provides some outlines, etc...
 * For the details see `org.jetbrains.plugins.scala.decompiler.Decompiler`)
 *
 * But [[ShowDecompiledClassAsJavaAction]] opens a decompiled `.class` file which has Java syntax.
 * The result is the same as if you invoke "Decompile to Java" action from editor notification when you open  `.class` file
 */
final class ShowDecompiledClassAsJavaAction extends AnAction(ScalaJavaDecompilerBundle.message("show.decompiled.class.as.java")) {

  override def actionPerformed(event: AnActionEvent): Unit = {
    val classFile = getClassfile(event)
    classFile.foreach(ScalaBytecodeDecompileTask.showDecompiledJavaCode)
  }

  override def update(event: AnActionEvent): Unit = {
    val project = event.getProject
    val classFile = getClassfile(event)
    val enabled = project != null && classFile.isDefined
    event.getPresentation.setEnabledAndVisible(enabled)
  }

  override def getActionUpdateThread: ActionUpdateThread = ActionUpdateThread.BGT

  private def getClassfile(event: AnActionEvent): Option[ScFile] = {
    val psiElement = getPsiElement(event)
    val psiFile = Option(event.getData(CommonDataKeys.PSI_FILE))

    val classFileFromPsiElement = psiElement.flatMap(filterScalaClassFile)
    classFileFromPsiElement
      .orElse(psiFile.flatMap(filterScalaClassFile))
      .orElse(psiElement.flatMap(classFileFromScalaClass))
  }

  /** inspired from [[org.jetbrains.java.decompiler.ShowDecompiledClassAction#getPsiElement]] */
  private def getPsiElement(e: AnActionEvent): Option[PsiElement] = {
    val editor = e.getData(CommonDataKeys.EDITOR)
    if (editor != null) {
      val psiFile = Option(PsiUtilBase.getPsiFileInEditor(editor, e.getProject))
      psiFile.flatMap(f => Option(f.findElementAt(editor.getCaretModel.getOffset)))
    }
    else Option(e.getData(CommonDataKeys.PSI_ELEMENT))
  }

  private def classFileFromScalaClass(psiElement: PsiElement): Option[ScFile] = {
    val clazz = PsiTreeUtil.getParentOfType(psiElement, classOf[ScTypeDefinition], false)
    if (clazz != null) {
      val compiledClass = getCompiledClassElement(clazz)
      val compiledClassFile = compiledClass.map(_.getContainingFile)
      compiledClassFile.flatMap(filterScalaClassFile)
    }
    else None
  }

  private[this] def filterScalaClassFile(element: PsiElement): Option[ScFile] =
    element match {
      case file: ScFile if file.isCompiled =>
        Option(file)
      case _ =>
        None
    }

  /** Inspired by [[com.intellij.psi.impl.source.PsiClassImpl.getOriginalElement]] */
  private def getCompiledClassElement(clazz: ScTypeDefinition): Option[PsiClass] = {
    val project = clazz.getProject
    if (DumbService.isDumb(project)) {
      // Avoid caching in dumb mode, as JavaPsiImplementationHelper.getOriginalClass depends on it
      return None
    }
    CachedValuesManager.getCachedValue(clazz, (() => {
      val helper = JavaPsiImplementationHelper.getInstance(project)
      val result = if (helper != null) Some(helper.getOriginalClass(clazz)) else None
      CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT)
    }): CachedValueProvider[Option[PsiClass]])
  }
}