package org.jetbrains.plugins.scala
package lang
package refactoring
package rename


import java.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Pass
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiReference}
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameJavaMemberProcessor
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.11.2008
 */

class RenameScalaVariableProcessor extends RenameJavaMemberProcessor with ScalaRenameProcessor {
  override def canProcessElement(element: PsiElement): Boolean = element match {
    case c: ScNamedElement => ScalaPsiUtil.nameContext(c) match {
      case _: ScVariable | _: ScValue | _: ScParameter => true
      case _: FakePsiMethod => true
      case _ => false
    }
    case _ => false
  }

  override def findReferences(element: PsiElement): util.ArrayList[PsiReference] = ScalaRenameUtil.findReferences(element)

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

    for (elem <- ScalaOverridingMemberSearcher.search(namedElement)) {
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
      case FakePsiMethod(method) => substituteElementToRename(method, editor)
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

  override def renameElement(element: PsiElement, newName: String, usages: Array[UsageInfo], listener: RefactoringElementListener) {
    ScalaRenameUtil.doRenameGenericNamedElement(element, newName, usages, listener)
  }
}
