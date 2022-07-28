package org.jetbrains.plugins.scala
package lang
package refactoring
package rename

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Pass
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.refactoring.rename.RenameJavaMemberProcessor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher

import java.util

class RenameScalaTypeAliasProcessor extends RenameJavaMemberProcessor with ScalaRenameProcessor {
  override def canProcessElement(element: PsiElement): Boolean = element.isInstanceOf[ScTypeAlias]

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    RenameSuperMembersUtil.chooseSuper(element.asInstanceOf[ScNamedElement])
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: Pass[PsiElement]): Unit = {
    val named = element match {
      case named: ScNamedElement => named
      case _ => return
    }
    RenameSuperMembersUtil.chooseAndProcessSuper(named, (named: PsiNamedElement) => {
      renameCallback.pass(named)
      false
    }, editor)
  }

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]): Unit = {
    val typeAlias = element match {
      case x: ScTypeAlias => x
      case _ => return
    }

    for (elem <- ScalaOverridingMemberSearcher.search(typeAlias, deep = true)) {
      allRenames.put(elem, newName)
    }

    ScalaElementToRenameContributor.addAllElements(element, newName, allRenames)
  }
}

