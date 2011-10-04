package org.jetbrains.plugins.scala
package lang
package refactoring
package rename


import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.refactoring.rename.RenameJavaMemberProcessor
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.usageView.UsageInfo
import java.util.{List, Map}
import psi.api.statements.{ScValue, ScVariable}
import psi.impl.search.ScalaOverridengMemberSearch
import psi.ScalaPsiUtil
import psi.api.statements.params.ScClassParameter
import psi.api.toplevel.{ScTypedDefinition, ScNamedElement}
import com.intellij.openapi.util.text.StringUtil
import psi.fake.FakePsiMethod

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.11.2008
 */

class RenameScalaValsProcessor extends RenameJavaMemberProcessor {
  override def canProcessElement(element: PsiElement): Boolean = element match {
    case c: ScNamedElement => ScalaPsiUtil.nameContext(c) match {
      case _: ScVariable => true 
      case _: ScValue => true
      case c: ScClassParameter if c.isVal || c.isVar => true
      case method: FakePsiMethod => true
      case _ => false
    }
    case _ => false
  }

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: Map[PsiElement, String]) {
    val namedElement = element match {case x: PsiNamedElement => x case _ => return}
    def addBeanMethods(element: PsiElement, newName: String) {
      element match {
        case t: ScTypedDefinition =>
          for (method <- t.getBeanMethods) {
            val name = method.getName
            val is = name.startsWith("is")
            val prefix = if (is) "is" else name.substring(0, 3)
            val newBeanName = prefix + StringUtil.capitalize(newName)
            allRenames.put(method, newBeanName)
          }
        case _ =>
      }
    }

    addBeanMethods(element, newName)
    
    for (elem <- ScalaOverridengMemberSearch.search(namedElement, true)) {
      val overriderName = elem.getName
      val baseName = namedElement.getName
      val newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName)
      if (newOverriderName != null) {
        allRenames.put(elem, newOverriderName)
        addBeanMethods(elem, newOverriderName)
      }
    }
  }


  override def findCollisions(element: PsiElement, newName: String, 
                              allRenames: Map[_ <: PsiElement, String], result: List[UsageInfo]) {/*todo*/}

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    element match {
      case method: FakePsiMethod => method.navElement
      case _ => element
    }
  }
}