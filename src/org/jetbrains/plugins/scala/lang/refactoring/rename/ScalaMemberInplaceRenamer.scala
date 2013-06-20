package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.openapi.editor.Editor
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * Nikolay.Tropin
 * 6/20/13
 */
class ScalaMemberInplaceRenamer(elementToRename: PsiNamedElement,
                                substituted: PsiElement,
                                editor: Editor,
                                initialName: String,
                                oldName: String)
        extends MemberInplaceRenamer(elementToRename, substituted, editor, initialName, oldName) {

  private def this(t: (PsiNamedElement, PsiElement, Editor, String, String)) = this(t._1, t._2, t._3, t._4, t._5)

  def this(elementToRename: PsiNamedElement, substituted: PsiElement, editor: Editor) {
    this {
      val name = ScalaNamesUtil.scalaName(substituted)
      (elementToRename, substituted, editor, name, name)
    }
  }

  protected override def createLookupExpression(): ScalaLookupExpression =
    new ScalaLookupExpression(getInitialName, myNameSuggestions, myElementToRename, shouldSelectAll, myAdvertisementText)


  protected override def getCommandName: String = {
    if (myInitialName != null) RefactoringBundle.message("renaming.command.name", myInitialName)
    else "Rename"
  }

}
