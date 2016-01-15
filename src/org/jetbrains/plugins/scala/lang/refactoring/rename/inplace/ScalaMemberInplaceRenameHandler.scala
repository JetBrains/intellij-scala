package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.LocalSearchScope
import com.intellij.refactoring.rename.inplace.{InplaceRefactoring, MemberInplaceRenameHandler, MemberInplaceRenamer}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper

/**
 * Nikolay.Tropin
 * 6/20/13
 */
class ScalaMemberInplaceRenameHandler extends MemberInplaceRenameHandler with ScalaInplaceRenameHandler {

  override def isAvailable(element: PsiElement, editor: Editor, file: PsiFile): Boolean = {
    val processor = renameProcessor(element)
    editor.getSettings.isVariableInplaceRenameEnabled && processor != null && processor.canProcessElement(element) && 
            !element.getUseScope.isInstanceOf[LocalSearchScope]
  }


  override def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) = {
    UsageTrigger.trigger(ScalaBundle.message("rename.member.id"))
    super.invoke(project, editor, file, dataContext)
  }

  protected override def createMemberRenamer(substituted: PsiElement,
                                             elementToRename: PsiNameIdentifierOwner,
                                             editor: Editor): MemberInplaceRenamer = {
    substituted match {
      case clazz: PsiClass if elementToRename.isInstanceOf[PsiClassWrapper] =>
        new ScalaMemberInplaceRenamer(elementToRename, clazz, editor)
      case clazz: PsiClass =>
        val companion = ScalaPsiUtil.getBaseCompanionModule(clazz)
        new ScalaMemberInplaceRenamer(clazz, companion.getOrElse(clazz), editor)
      case subst: PsiNamedElement => new ScalaMemberInplaceRenamer(elementToRename, subst, editor)
      case _ => throw new IllegalArgumentException("Substituted element for renaming has no name")
    }
  }

  override def doRename(elementToRename: PsiElement, editor: Editor, dataContext: DataContext): InplaceRefactoring = {
    afterElementSubstitution(elementToRename, editor, dataContext) {
      super.doRename(_, editor, dataContext)
    }
  }
}
