package org.jetbrains.plugins.scala.lang.refactoring.move

import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.refactoring.move.moveClassesOrPackages.MoveAllClassesInFileHandler
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

import java.lang.{Boolean => JBoolean}
import java.{util => ju}

final class MoveScalaClassesInFileHandler extends MoveAllClassesInFileHandler {

  override def processMoveAllClassesInFile(allClasses: ju.Map[PsiClass, JBoolean],
                                           psiClass: PsiClass,
                                           elementsToMove: PsiElement*): Unit = {
    psiClass.getContainingFile match {
      case file: ScalaFile if ScalaApplicationSettings.getInstance.MOVE_COMPANION =>
        for {
          companion <- companionModule(psiClass, withCompanion = true)
          if !elementsToMove.contains(companion) && file.typeDefinitions.toSet == Set(psiClass, companion)
        } allClasses.put(psiClass, true)
      case file: ScalaFile if allClasses.get(psiClass) =>
        //if move destination contains file with such name, we will try to move classes, not a whole file

        MoveDestination(psiClass).findFile(file.getName) match {
          case null =>
          case _ => allClasses.put(psiClass, false)
        }
      case _ =>
    }
  }
}
