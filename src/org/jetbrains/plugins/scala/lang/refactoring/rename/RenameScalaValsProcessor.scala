package org.jetbrains.plugins.scala
package lang
package refactoring
package rename


import com.intellij.openapi.editor.Editor
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
import extensions.toPsiNamedElementExt
import com.intellij.refactoring.rename.RenameJavaMemberProcessor
import com.intellij.psi.{PsiReference, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._
import psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{PsiElementProcessor, LocalSearchScope}
import com.intellij.refactoring.listeners.RefactoringElementListener
import org.jetbrains.plugins.scala.util.SuperMemberUtil
import com.intellij.openapi.util.Pass
import java.util

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
    val (localScope, onlyLocal) = element match {
      case p: ScClassParameter if p.isPrivateThis => (new LocalSearchScope(p.containingClass), true)
      case m: ScMember if m.isPrivate => (new LocalSearchScope(m.containingClass), true)
      case _ => (new LocalSearchScope(element.getContainingFile), false)
    }

    ScalaRenameUtil.filterAliasedReferences {
      val local = ReferencesSearch.search(element, localScope, true).findAll()
      if (onlyLocal) local
      else {
        /* In GlobalSearchScope only cannot find reference like this:
        * var a = 0
        * a_=(1)
        */
        val buf = new util.HashSet[PsiReference]
        buf.addAll(local)
        buf.addAll(super.findReferences(element))
        buf
      }
    }
  }

  override def prepareRenaming(element: PsiElement, newName: String, allRenames: Map[PsiElement, String]) {
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
                  val newBeanName = prefix + StringUtil.capitalize(newName)
                  allRenames.put(wrapper, newBeanName)
                }
              )
            case _ =>
          }
        case _ =>
      }
    }

    def addScalaSetter(element: PsiElement, newName: String): Unit = {
      element match {
        case t: ScTypedDefinition =>
          val underEq = t.getUnderEqualsMethod
          val wrapper = t.getTypedDefinitionWrapper(isStatic = false, isInterface = false, EQ, None)
          allRenames.put(underEq, newName + "_=")
          allRenames.put(wrapper, newName + "_$eq")
        case _ =>
      }
    }

    addBeanMethods(element, newName)
//    addScalaSetter(namedElement, newName)

    for (elem <- ScalaOverridengMemberSearch.search(namedElement, deep = true)) {
      val overriderName = elem.name
      val baseName = namedElement.name
      val newOverriderName = RefactoringUtil.suggestNewOverriderName(overriderName, baseName, newName)
      if (newOverriderName != null) {
        allRenames.put(elem, newOverriderName)
        addBeanMethods(elem, newOverriderName)
//        addScalaSetter(elem, newOverriderName)
      }
    }
  }

  override def findCollisions(element: PsiElement, newName: String,
                              allRenames: Map[_ <: PsiElement, String], result: List[UsageInfo]) {/*todo*/}

  override def substituteElementToRename(element: PsiElement, editor: Editor): PsiElement = {
    element match {
      case method: FakePsiMethod => substituteElementToRename(method.navElement, editor)
      case named: ScNamedElement => SuperMemberUtil.chooseSuper(named, "Choose element to rename")
      case _ => element
    }
  }

  override def substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: Pass[PsiElement]) {
    val named = element match {case named: ScNamedElement => named; case _ => return}
    SuperMemberUtil.chooseAndProcessSuper(named, new PsiElementProcessor[PsiNamedElement] {
      def execute(named: PsiNamedElement): Boolean = {
        renameCallback.pass(named)
        false
      }
    }, "Choose element to rename", editor)
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