package org.jetbrains.plugins.scala
package lang.refactoring.rename

import java.util

import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import javax.swing.Icon
import org.jetbrains.annotations.{Nls, NotNull}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaration, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project.UserDataHolderExt

import scala.collection.mutable

/**
 * Nikolay.Tropin
 * 9/19/13
 */
object RenameSuperMembersUtil {

  private val superMembersToRename: mutable.Set[PsiElement] = mutable.Set.empty

  private val renameAllKey: Key[ScObject] = Key.create("rename.all.marker")
  private def renameAllMarker(element: PsiElement): ScObject = {
    val project = element.getProject
    project.getOrUpdateUserData(renameAllKey,
      ScalaPsiElementFactory.createScalaFileFromText("object RenameAll")(project).typeDefinitions.head.asInstanceOf[ScObject]
    )
  }

  def chooseSuper(named: ScNamedElement): PsiNamedElement = {
    var chosen: PsiNamedElement = null
    val processor = new PsiElementProcessor[PsiNamedElement]() {
      override def execute(element: PsiNamedElement): Boolean = {
        chosen = element
        false
      }
    }
    chooseAndProcessSuper(named, processor, null)
    chosen
  }

  def chooseAndProcessSuper(named: ScNamedElement, processor: PsiElementProcessor[PsiNamedElement], editor: Editor): Unit = {

    val superMembers = named match {
      case _: ScTypeAlias => allSuperTypes(named, withSelfType = false)
      case _ => allSuperMembers(named, withSelfType = false)
    }
    val maxSuperMembers = findMaxSuperMembers(superMembers)
    if (maxSuperMembers.isEmpty || maxSuperMembers == Seq(named)) {
      processor.execute(named)
      return
    }
    afterChoosingSuperMember(maxSuperMembers, named, editor)(processor.execute)
  }

  def prepareSuperMembers(element: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]): Unit = {
    for (elem <- superMembersToRename) {
      allRenames.put(elem, newName)
      superMembersToRename -= elem
      import scala.collection.JavaConverters._
      RenamePsiElementProcessor.allForElement(elem).asScala.foreach(_.prepareRenaming(elem, newName, allRenames))
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
    val classes = allElements.flatMap(_.parentOfType(classOf[PsiClass], strict = false))

    val oneSuperClass = superMembers.size == 1
    val additional = if (oneSuperClass) Nil else Seq((renameAllMarker(element), null)) //option for rename all
    val classesToNamed = additional ++: Map(classes.zip(allElements): _*)
    val selection = classesToNamed.keys.head

    val processor = new PsiElementProcessor[PsiClass] {
      override def execute(aClass: PsiClass): Boolean = {
        if (aClass != renameAllMarker(aClass)) action(classesToNamed(aClass))
        else {
          val mainOne = classesToNamed(classes.head)
          superMembersToRename.clear()
          superMembersToRename ++= classes.dropRight(1).drop(1).map(classesToNamed)
          action(mainOne)
        }
        false
      }
    }

    if (ApplicationManager.getApplication.isUnitTestMode) {
      processor.execute(if (oneSuperClass) classes.head else renameAllMarker(element)) //in unit tests uses base member or all base members
      return
    }

    val renameAllText = ScalaBundle.message("rename.all.base.members")
    val renameBase = ScalaBundle.message("rename.base.member")
    val renameOnlyCurrent = ScalaBundle.message("rename.only.current.member")
    val name = ScalaNamesUtil.scalaName(superMembers.last)
    @Nls
    val title =
      if (oneSuperClass) {
        val qualName = classes.head.qualifiedName

        ScalaPsiUtil.nameContext(superMembers.head) match {
          case _: ScDeclaration => ScalaBundle.message("name.implements.member.of.qualname", name, qualName)
          case _ => ScalaBundle.message("name.overrides.member.of.qualname", name, qualName)
        }
      }
      else ScalaBundle.message("rename.has.multiple.base.members", name)

    val popup = NavigationUtil.getPsiElementPopup(classesToNamed.keys.toArray, new PsiClassListCellRenderer() {
      override def getIcon(element: PsiElement): Icon = {
        if (element == renameAllMarker(element) || oneSuperClass) null
        else super.getIcon(element)
      }

      override def getElementText(clazz: PsiClass): String = {
        if (clazz == renameAllMarker(clazz)) return renameAllText
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
        if (clazz == renameAllMarker(clazz) || clazz == classes.last || oneSuperClass) null //don't show package name
        else super.getContainerText(clazz, name)
      }
    }, title, processor, selection)

    if (editor != null) popup.showInBestPositionFor(editor)
    else popup.showInFocusCenter()
  }

  @NotNull
  def allSuperMembers(named: ScNamedElement, withSelfType: Boolean): Seq[PsiNamedElement] = {
    val member = ScalaPsiUtil.nameContext(named) match {
      case m: ScMember => m
      case _ => return Seq.empty
    }
    val aClass = member.containingClass
    val signatures =
      if (withSelfType) TypeDefinitionMembers.getSelfTypeSignatures(aClass)
      else TypeDefinitionMembers.getSignatures(aClass)
    val allSigns = signatures.forName(named.name)

    allSigns.findNode(named) match {
      case Some(node) => node.supers.map(_.info.namedElement)
      case _ => Seq.empty
    }
  }

  @NotNull
  def allSuperTypes(named: ScNamedElement, withSelfType: Boolean): Seq[PsiNamedElement] = {
    val typeAlias = ScalaPsiUtil.nameContext(named) match {
      case t: ScTypeAlias => t
      case _ => return Seq()
    }
    val aClass = typeAlias.containingClass
    val types =
      if (withSelfType) TypeDefinitionMembers.getSelfTypeTypes(aClass)
      else TypeDefinitionMembers.getTypes(aClass)
    val forName = types.forName(named.name)

    forName.findNode(typeAlias) match {
      case Some(node) => node.supers.map(_.info.namedElement)
      case _ => Seq.empty
    }
  }

  @NotNull
  private def findMaxSuperMembers(elements: Seq[PsiNamedElement]): Seq[PsiNamedElement] = {
    def elementWithContainingClass(elem: PsiNamedElement) = {
      ScalaPsiUtil.nameContext(elem) match {
        case sm: ScMember => Option(sm.containingClass, elem)
        case m: PsiMember => Option((m.getContainingClass, elem))
        case _ => None
      }
    }
    val classToElement = elements.flatMap(elementWithContainingClass).toMap
    val classes = classToElement.keys
    val maxClasses = classes.filter(maxClass => !classes.exists(maxClass.isInheritor(_, /*deep = */ true)))
    maxClasses.flatMap(classToElement.get).toSeq
  }
}
