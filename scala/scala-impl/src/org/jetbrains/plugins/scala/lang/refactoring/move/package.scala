package org.jetbrains.plugins.scala
package lang
package refactoring

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiClass, PsiDirectory, PsiElement}
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.conversion.copy.{Associations, ScalaCopyPastePostProcessor}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaFile}

package object move {

  private val PROCESSOR = new ScalaCopyPastePostProcessor
  private val ASSOCIATIONS_KEY: Key[Associations] = Key.create("ASSOCIATIONS")

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
      applyWithCompanionModule(clazz, withCompanion) { clazz =>
        val range = clazz.getTextRange
        val associations = PROCESSOR.collectTransferableData(
          Array(range.getStartOffset),
          Array(range.getEndOffset)
        )(file, null)
        clazz.putCopyableUserData(ASSOCIATIONS_KEY, associations.orNull)
      }
    }

  def restoreAssociations(@NotNull aClass: PsiClass): Unit =
    applyWithCompanionModule(aClass, moveCompanion) { clazz =>
      Option(clazz.getCopyableUserData(ASSOCIATIONS_KEY)).foreach {
        try {
          PROCESSOR.restoreAssociations(_, clazz.getContainingFile, clazz.getTextRange.getStartOffset, clazz.getProject)
        } finally {
          clazz.putCopyableUserData(ASSOCIATIONS_KEY, null)
        }
      }
    }

  def shiftAssociations(aClass: PsiClass, offsetChange: Int): Unit =
    aClass.getCopyableUserData(ASSOCIATIONS_KEY) match {
      case null =>
      case as: Associations => as.associations.foreach(a => a.range = a.range.shiftRight(offsetChange))
    }

  def saveMoveDestination(@NotNull element: PsiElement, moveDestination: PsiDirectory): Unit = {
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
