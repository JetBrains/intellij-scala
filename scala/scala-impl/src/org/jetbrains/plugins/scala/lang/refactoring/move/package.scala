package org.jetbrains.plugins.scala
package lang
package refactoring

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiClass, PsiDirectory, PsiElement}
import org.jetbrains.plugins.scala.conversion.copy.Associations
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaFile}

package object move {

  object MoveDestination {

    private val key = Key.create[PsiDirectory]("MoveDestination")

    def apply(element: PsiElement): PsiDirectory = element.getUserData(key)

    def update(element: PsiElement, destination: PsiDirectory): Unit = {
      element.putUserData(key, destination)
    }
  }

  import util.ScalaChangeContextUtil._

  def collectAssociations(clazz: PsiClass,
                          file: ScalaFile,
                          withCompanion: Boolean): Unit =
    if (file.getContainingDirectory != MoveDestination(clazz)) {
      applyWithCompanionModule(clazz, withCompanion) { clazz =>
        AssociationsData(clazz) = collectDataForElement(clazz)
      }
    }

  def restoreAssociations(aClass: PsiClass): Unit =
    applyWithCompanionModule(aClass, moveCompanion) { clazz =>
      AssociationsData(clazz) match {
        case null =>
        case Associations(associations) =>
          try {
            processor.doRestoreAssociations(associations, clazz.getContainingFile, clazz.getTextRange.getStartOffset, clazz.getProject)(identity)
          } finally {
            AssociationsData(clazz) = null
          }

      }
    }

  def saveMoveDestination(element: PsiElement, moveDestination: PsiDirectory): Unit = {
    val classes = element match {
      case c: PsiClass => Seq(c)
      case f: ScalaFile => f.typeDefinitions
      case p: ScPackage => p.getClasses.toSeq
      case _ => Seq.empty
    }

    classes.foreach {
      applyWithCompanionModule(_, withCompanion = true) {
        MoveDestination(_) = moveDestination
      }
    }
  }

  private def applyWithCompanionModule(clazz: PsiClass, withCompanion: Boolean)
                                      (function: PsiClass => Unit): Unit =
    (Option(clazz) ++ companionModule(clazz, withCompanion)).foreach(function)

  def companionModule(clazz: PsiClass, withCompanion: Boolean): Option[ScTypeDefinition] =
    Option(clazz).collect {
      case definition: ScTypeDefinition if withCompanion => definition
    }.flatMap {
      _.baseCompanionModule
    }

  def moveCompanion: Boolean = settings.ScalaApplicationSettings.getInstance.MOVE_COMPANION
}
