package org.jetbrains.plugins.scala
package lang
package refactoring
package rename


import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiMember, PsiElement, PsiNamedElement}
import com.intellij.refactoring.rename.{MemberHidesStaticImportUsageInfo, RenameJavaMethodProcessor, RenameJavaMemberProcessor}
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.usageView.UsageInfo
import java.util.{List, Map}
import psi.api.statements.{ScFunction, ScValue, ScVariable}
import psi.api.toplevel.ScNamedElement
import psi.impl.search.ScalaOverridengMemberSearch
import psi.ScalaPsiUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.11.2008
 */

class RenameScalaValsProcessor extends RenameJavaMemberProcessor {
  override def canProcessElement(element: PsiElement): Boolean = element match {
    case c: ScNamedElement => ScalaPsiUtil.nameContext(c) match {case _: ScVariable => true case _: ScValue => true case _ => false}
    case _ => false
  }

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: Map[PsiElement, String]): Unit = {
    val namedElement = element match {case x: PsiNamedElement => x case _ => return}
    for (elem <- ScalaOverridengMemberSearch.search(namedElement, true)) {
      val overriderName = elem.getName
      val baseName = namedElement.getName
      val newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName)
      if (newOverriderName != null) {
        allRenames.put(elem, newOverriderName)
      }
    }
  }


  override def findCollisions(element: PsiElement, newName: String, allRenames: Map[_ <: PsiElement, String], result: List[UsageInfo]): Unit = {/*todo*/}

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = element //todo:
}