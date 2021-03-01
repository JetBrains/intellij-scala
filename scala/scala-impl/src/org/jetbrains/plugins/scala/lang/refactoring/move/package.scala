package org.jetbrains.plugins.scala
package lang
package refactoring

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiClass, PsiDirectory, PsiElement}
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

  def collectAssociations(clazz: PsiClass,
                          file: ScalaFile,
                          withCompanion: Boolean): Unit =
    if (file.getContainingDirectory != MoveDestination(clazz)) {
      applyWithCompanionModule(clazz, withCompanion)(util.ScalaChangeContextUtil.encodeContextInfo)
    }

  def restoreAssociations(clazz: PsiClass): Unit =
    applyWithCompanionModule(clazz, moveCompanion)(Associations.restoreFor)

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

  def applyWithCompanionModule(clazz: PsiClass, withCompanion: Boolean)
                              (function: PsiClass => Unit): Unit =
    (Option(clazz) ++ companionModule(clazz, withCompanion)).foreach(function)

  def companionModule(clazz: PsiClass, withCompanion: Boolean): Option[ScTypeDefinition] =
    Option(clazz).collect {
      case definition: ScTypeDefinition if withCompanion => definition
    }.flatMap {
      _.baseCompanion
    }

  def moveCompanion: Boolean = settings.ScalaApplicationSettings.getInstance.MOVE_COMPANION
}
