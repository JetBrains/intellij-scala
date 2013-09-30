package org.jetbrains.plugins.scala
package util

import com.intellij.psi._
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScObject, ScMember}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypedDefinition, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * Nikolay.Tropin
 * 9/19/13
 */
object SuperMemberUtil {

  def chooseSuper(named: ScNamedElement, title: String): PsiNamedElement = {
    var chosen: PsiNamedElement = null
    val processor = new PsiElementProcessor[PsiNamedElement]() {
      def execute(element: PsiNamedElement): Boolean = {
        chosen = element
        false
      }
    }
    chooseAndProcessSuper(named, processor, title, null)
    chosen
  }

  def chooseAndProcessSuper(named: ScNamedElement, processor: PsiElementProcessor[PsiNamedElement], title: String, editor: Editor) {

    val superMembers = named +: allSuperMembers(named, withSelfType = false)
    if (superMembers.isEmpty) {
      processor.execute(named)
      return
    }

    afterChoosingSuperMember(superMembers, title, editor) {
      processor.execute(_)
    }
  }

  private def afterChoosingSuperMember(superMembers: Seq[PsiNamedElement], title: String, editor: Editor)(action: PsiNamedElement => Unit): Unit = {
    if (superMembers == null || superMembers.isEmpty) return
    if (superMembers.length == 1) {
      action(superMembers(0))
      return
    }
    if (ApplicationManager.getApplication.isUnitTestMode) {
      action(superMembers(1)) //in unit tests uses base member
      return
    }
    val classes: Seq[PsiClass] = superMembers.map(PsiTreeUtil.getParentOfType(_, classOf[PsiClass], false))
    val classesToNamed = Map(classes.zip(superMembers): _*)
    val selection = classes(0)
    val processor = new PsiElementProcessor[PsiClass] {
      def execute(aClass: PsiClass): Boolean = {
        action(classesToNamed(aClass))
        false
      }
    }
    val popup = NavigationUtil.getPsiElementPopup(classes.toArray, new PsiClassListCellRenderer() {
      override def getElementText(element: PsiClass): String = {
        val named = classesToNamed(element)
        val kind = ScalaPsiUtil.nameContext(named) match {
          case _: ScFunction => "def "
          case td: ScTypedDefinition =>
            if (td.isVal) "val "
            else if (td.isVar) "var "
            else ""
          case _ => ""
        }
        val classKind = element match {
          case _: ScObject => "object"
          case _: ScTrait => "trait"
          case _ => "class"
        }
        val in = if (element == classes(0)) s" in current $classKind " else s" in $classKind "
        kind + ScalaNamesUtil.scalaName(named) + in + super.getElementText(element).replace("$", "")
      }
    }, title, processor, selection)
    if (editor != null) popup.showInBestPositionFor(editor)
    else popup.showInFocusCenter()
  }

  def allSuperMembers(named: ScNamedElement, withSelfType: Boolean): Seq[PsiNamedElement] = {
    val member = ScalaPsiUtil.nameContext(named) match {
      case m: ScMember => m
      case _ => return Seq()
    }
    val aClass = member.containingClass
    if (aClass == null) return Seq()
    val signatures =
      if (withSelfType) TypeDefinitionMembers.getSelfTypeSignatures(aClass)
      else TypeDefinitionMembers.getSignatures(aClass)
    val allSigns = signatures.forName(named.name)._1
    val signs = allSigns.filter(sign => sign._1.namedElement.exists(named ==))
    signs.flatMap(sign => sign._2.supers.map(_.info.namedElement.get))
  }
}
