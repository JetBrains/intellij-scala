package org.jetbrains.plugins.scala
package lang
package refactoring
package rename


import com.intellij.openapi.editor.Editor
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.usageView.UsageInfo
import psi.api.statements.{ScValue, ScVariable}
import psi.impl.search.ScalaOverridengMemberSearch
import psi.ScalaPsiUtil
import psi.api.statements.params.ScClassParameter
import psi.api.toplevel.{ScTypedDefinition, ScNamedElement}
import com.intellij.openapi.util.text.StringUtil
import psi.fake.FakePsiMethod
import extensions.toPsiNamedElementExt
import com.intellij.refactoring.rename.RenameJavaMemberProcessor
import com.intellij.psi.{PsiReference, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._
import psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{GlobalSearchScope, PsiElementProcessor, LocalSearchScope}
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.openapi.util.Pass
import java.util
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.11.2008
 */

class RenameScalaValsProcessor extends RenameJavaMemberProcessor {
  override def canProcessElement(element: PsiElement): Boolean = element match {
    case c: ScNamedElement => ScalaPsiUtil.nameContext(c) match {
      case _: ScVariable | _: ScValue | _: ScClassParameter => true
      case method: FakePsiMethod => true
      case _ => false
    }
    case _ => false
  }

  override def findReferences(element: PsiElement) = {
    val isClassParameter = element.isInstanceOf[ScClassParameter]
    val (localScope, onlyLocal) = element match {
      case m: ScMember if m.isPrivate => (new LocalSearchScope(m.containingClass), true)
      case _ => (new LocalSearchScope(element.getContainingFile), false)
    }

    ScalaRenameUtil.filterAliasedReferences {
      /*todo Search in GlobalSearchScope only cannot find reference like this:
      * var a = 0
      * a_=(1)
      * But search in local scope finds it
      */
      val local = ReferencesSearch.search(element, localScope, true).findAll()
      lazy val global = ReferencesSearch.search(element, GlobalSearchScope.allScope(element.getProject), isClassParameter).findAll
      if (onlyLocal && !isClassParameter) local
      else {
        val buf = new util.HashSet[PsiReference]
        buf.addAll(local)
        buf.addAll(global)
        buf
      }
    }
  }

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]) {
    val namedElement = element match {case x: PsiNamedElement => x case _ => return}
    def addBeanMethods(element: PsiElement, newName: String) {
      element match {
        case t: ScTypedDefinition =>
          for (method <- t.getBeanMethods) {
            val name = method.name
            val is = name.startsWith("is")
            val prefix = if (is) "is" else name.substring(0, 3)
            val newBeanName = prefix + StringUtil.capitalize(newName)
            allRenames.put(method, newBeanName)
          }
          t.nameContext match {
            case member: ScMember if member.containingClass != null =>
              Seq(GETTER, SETTER, IS_GETTER).foreach(
                r => {
                  val wrapper = t.getTypedDefinitionWrapper(isStatic = false, isInterface = false, r, None)
                  val name = wrapper.getName
                  val is = name.startsWith("is")
                  val prefix = if (is) "is" else name.substring(0, 3)
                  val newBeanName = prefix + StringUtil.capitalize(ScalaNamesUtil.toJavaName(newName))
                  allRenames.put(wrapper, newBeanName)
                }
              )
            case _ =>
          }
        case _ =>
      }
    }

    addBeanMethods(element, newName)

    for (elem <- ScalaOverridengMemberSearch.search(namedElement, deep = true)) {
      val overriderName = elem.name
      val baseName = namedElement.name
      val newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName)
      if (newOverriderName != null) {
        allRenames.put(elem, newOverriderName)
        addBeanMethods(elem, newOverriderName)
      }
    }
    RenameSuperMembersUtil.prepareSuperMembers(element, newName, allRenames)
  }
  override def findCollisions(element: PsiElement, newName: String,
                              allRenames: util.Map[_ <: PsiElement, String], result: util.List[UsageInfo]) {/*todo*/}

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    element match {
      case method: FakePsiMethod => substituteElementToRename(method.navElement, editor)
      case named: ScNamedElement => RenameSuperMembersUtil.chooseSuper(named)
      case _ => element
    }
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: Pass[PsiElement]) {
    val named = element match {case named: ScNamedElement => named; case _ => return}
    RenameSuperMembersUtil.chooseAndProcessSuper(named, new PsiElementProcessor[PsiNamedElement] {
      def execute(named: PsiNamedElement): Boolean = {
        renameCallback.pass(named)
        false
      }
    }, editor)
  }

  override def setToSearchInComments(element: PsiElement, enabled: Boolean) {
    ScalaApplicationSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_AND_STRINGS = enabled
  }

  override def isToSearchInComments(psiElement: PsiElement): Boolean = {
    ScalaApplicationSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_AND_STRINGS
  }

  override def renameElement(element: PsiElement, newName: String, usages: Array[UsageInfo], listener: RefactoringElementListener) {
    ScalaRenameUtil.doRenameGenericNamedElement(element, newName, usages, listener)
  }
}