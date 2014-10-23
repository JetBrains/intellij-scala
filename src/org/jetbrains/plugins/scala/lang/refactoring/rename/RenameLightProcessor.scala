package org.jetbrains.plugins.scala.lang.refactoring.rename

import java.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.{PsiTypedDefinitionWrapper, ScFunctionWrapper, StaticPsiMethodWrapper, StaticPsiTypedDefinitionWrapper}

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.09.2009
 */
class RenameLightProcessor extends RenamePsiElementProcessor {
  def canProcessElement(element: PsiElement): Boolean = {
    element match {
      case f: FakePsiMethod => true
      case f: ScFunctionWrapper => true
      case d: PsiTypedDefinitionWrapper => true
      case d: StaticPsiTypedDefinitionWrapper => true
      case p: StaticPsiMethodWrapper => true
      case _ => false
    }
  }


  override def prepareRenaming(element: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]) {
    val orig = originalElement(element)
    allRenames.put(orig, newName)
    import scala.collection.JavaConverters.asScalaBufferConverter
    for (processor <- RenamePsiElementProcessor.allForElement(orig).asScala) {
      processor.prepareRenaming(orig, newName, allRenames)
    }
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    val orig = originalElement(element)
    if (orig != null) {
      val processor = RenamePsiElementProcessor.forElement(orig)
      processor.substituteElementToRename(orig, editor)
    } else null
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: Pass[PsiElement]): Unit = {
    val orig = originalElement(element)
    if (orig != null) {
      val processor = RenamePsiElementProcessor.forElement(orig)
      processor.substituteElementToRename(orig, editor, renameCallback)
    }
  }

  private def originalElement(element: PsiElement) = element match {
    case f: FakePsiMethod => null
    case f: ScFunctionWrapper => f.function
    case d: PsiTypedDefinitionWrapper => d.typedDefinition
    case d: StaticPsiTypedDefinitionWrapper => d.typedDefinition
    case p: StaticPsiMethodWrapper => p.method
    case _ => element
  }

  override def renameElement(element: PsiElement, newName: String, usages: Array[UsageInfo], listener: RefactoringElementListener) {
    ScalaRenameUtil.doRenameGenericNamedElement(element, newName, usages, listener)
  }
}