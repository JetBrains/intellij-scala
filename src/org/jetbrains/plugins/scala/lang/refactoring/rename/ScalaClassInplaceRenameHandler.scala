package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.refactoring.rename.inplace.{MemberInplaceRenamer, MemberInplaceRenameHandler}
import com.intellij.psi.{PsiFile, PsiNameIdentifierOwner, PsiElement}
import com.intellij.openapi.editor.Editor

/**
 * Nikolay.Tropin
 * 6/18/13
 */
class ScalaClassInplaceRenameHandler extends MemberInplaceRenameHandler {

  override def isAvailable(element: PsiElement, editor: Editor, file: PsiFile): Boolean = {
    editor.getSettings.isVariableInplaceRenameEnabled &&
            new RenameScalaClassProcessor().canProcessElement(element)
  }

  protected override def createMemberRenamer(element: PsiElement,
                                                      elementToRename: PsiNameIdentifierOwner,
                                                      editor: Editor): MemberInplaceRenamer = {
    new ScalaClassInplaceRenamer(elementToRename, element, editor)
  }

}
