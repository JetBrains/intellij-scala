package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.psi._
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTrait, ScObject, ScMember}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaration
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import javax.swing.Icon
import org.jetbrains.annotations.NotNull
import scala.collection.mutable
import java.util
import com.intellij.refactoring.rename.RenamePsiElementProcessor

/**
 * Nikolay.Tropin
 * 9/19/13
 */
object RenameSuperMembersUtil {

  val superMembersToRename =  mutable.Set[PsiElement]()

  def chooseSuper(named: ScNamedElement): PsiNamedElement = {
    var chosen: PsiNamedElement = null
    val processor = new PsiElementProcessor[PsiNamedElement]() {
      def execute(element: PsiNamedElement): Boolean = {
        chosen = element
        false
      }
    }
    chooseAndProcessSuper(named, processor, null)
    chosen
  }

  def chooseAndProcessSuper(named: ScNamedElement, processor: PsiElementProcessor[PsiNamedElement], editor: Editor) {

    val superMembers = allSuperMembers(named, withSelfType = false)
    val maxSuperMembers = findMaxSuperMembers(superMembers)
    if (maxSuperMembers.isEmpty || maxSuperMembers == Seq(named))  {
      processor.execute(named)
      return
    }
    afterChoosingSuperMember(maxSuperMembers, named, editor) {
      processor.execute(_)
    }
  }

  def prepareSuperMembers(element: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]) = {
    for (elem <- superMembersToRename) {
      allRenames.put(elem, newName)
      superMembersToRename -= elem
      RenamePsiElementProcessor.forElement(elem).prepareRenaming(elem, newName, allRenames)
    }
  }

  /* @param supermembers contains only maximal supermembers
   */
  private def afterChoosingSuperMember(superMembers: Seq[PsiNamedElement], element: PsiNamedElement, editor: Editor)
                                      (action: PsiNamedElement => Unit): Unit = {
    if (superMembers.isEmpty) {
      action(element)
      return
    }
    val allElements = superMembers :+ element
    val classes: Seq[PsiClass] = allElements.map(PsiTreeUtil.getParentOfType(_, classOf[PsiClass], false))
    val oneSuperClass = superMembers.size == 1
    val renameAllMarkerObject = ScalaPsiElementFactory.createObjectWithContext("object RenameAll", classes.last.getContainingFile, classes.last)
    val additional = if (oneSuperClass) Nil else Seq((renameAllMarkerObject, null)) //option for rename all
    val classesToNamed = additional ++: Map(classes.zip(allElements): _*)
    val selection = classesToNamed.keys.head

    val processor = new PsiElementProcessor[PsiClass] {
      def execute(aClass: PsiClass): Boolean = {
        if (aClass != renameAllMarkerObject) action(classesToNamed(aClass))
        else {
          val mainOne = classesToNamed(classes(0))
          superMembersToRename.clear()
          superMembersToRename ++= classes.dropRight(1).drop(1).map(classesToNamed)
          action(mainOne)
        }
        false
      }
    }
    val renameAllText = ScalaBundle.message("rename.all.base.members")
    val renameBase = ScalaBundle.message("rename.base.member")
    val renameOnlyCurrent = ScalaBundle.message("rename.only.current.member")
    val name = ScalaNamesUtil.scalaName(superMembers.last)
    val title =
      if (oneSuperClass) {
        val overimpl = ScalaPsiUtil.nameContext(superMembers(0)) match {
          case decl: ScDeclaration => "implements"
          case _ => "overrides"
        }
        val qualName = classes(0) match {
          case td: ScTypeDefinition => td.qualifiedName
          case cl => cl.getQualifiedName
        }
        s"$name $overimpl member of $qualName"
      }
      else ScalaBundle.message("rename.has.multiple.base.members", name)

    val popup = NavigationUtil.getPsiElementPopup(classesToNamed.keys.toArray, new PsiClassListCellRenderer() {
      override def getIcon(element: PsiElement): Icon = {
        if (element == renameAllMarkerObject || oneSuperClass) null
        else super.getIcon(element)
      }

      override def getElementText(clazz: PsiClass): String = {
        if (clazz == renameAllMarkerObject) return renameAllText
        def classKind = clazz match {
          case _: ScObject => "object"
          case _: ScTrait => "trait"
          case _ => "class"
        }
        if (clazz == classes.last) renameOnlyCurrent
        else if (oneSuperClass) renameBase
        else ScalaBundle.message("rename.only.in", classKind, ScalaNamesUtil.scalaName(clazz))
      }

      override def getContainerText(clazz: PsiClass, name: String): String = {
        if (clazz == renameAllMarkerObject || clazz == classes.last || oneSuperClass) null //don't show package name
        else super.getContainerText(clazz, name)
      }
    }, title, processor, selection)

    if (ApplicationManager.getApplication.isUnitTestMode) {
      processor.execute(if (oneSuperClass) classes(0) else renameAllMarkerObject) //in unit tests uses base member or all base members
      return
    }
    if (editor != null) popup.showInBestPositionFor(editor)
    else popup.showInFocusCenter()
  }

  @NotNull
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

  @NotNull
  private def findMaxSuperMembers(elements: Seq[PsiNamedElement]): Seq[PsiNamedElement] = {
    def elementWithContainingClass(elem: PsiNamedElement): Option[(PsiClass, PsiNamedElement)] = {
      ScalaPsiUtil.nameContext(elem) match {
        case sm: ScMember => Option(sm.containingClass, elem)
        case m: PsiMember => Option((m.getContainingClass, elem))
        case _ => None
      }
    }
    val classToElement = elements.flatMap(elementWithContainingClass).toMap
    val classes = classToElement.keys
    val maxClasses = classes.filter(maxClass => !classes.exists(maxClass.isInheritor(_, /*deep = */true)))
    maxClasses.flatMap(classToElement.get).toSeq
  }
}
