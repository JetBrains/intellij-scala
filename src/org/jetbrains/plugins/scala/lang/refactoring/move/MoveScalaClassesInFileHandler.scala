package org.jetbrains.plugins.scala
package lang.refactoring.move

import java.lang.Boolean
import java.util

import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.refactoring.move.moveClassesOrPackages.MoveAllClassesInFileHandler
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
 * Nikolay.Tropin
 * 10/25/13
 */
class MoveScalaClassesInFileHandler extends MoveAllClassesInFileHandler {

  def processMoveAllClassesInFile(allClasses: util.Map[PsiClass, Boolean],
                                  psiClass: PsiClass,
                                  elementsToMove: PsiElement*): Unit = {
    psiClass.getContainingFile match {
      case file: ScalaFile if ScalaApplicationSettings.getInstance().MOVE_COMPANION =>
        val classesInFile = file.typeDefinitions.toSet
        ScalaPsiUtil.getBaseCompanionModule(psiClass) match {
          case Some(companion) if !elementsToMove.contains(companion) && classesInFile == Set(psiClass, companion) =>
              allClasses.put(psiClass, true)
          case _ =>
        }
      case file: ScalaFile if allClasses.get(psiClass) =>
        //if move destination contains file with such name, we will try to move classes, not a whole file
        val moveDestination = psiClass.getUserData(ScalaMoveUtil.MOVE_DESTINATION)
        if (moveDestination.findFile(file.getName) != null)
          allClasses.put(psiClass, false)
      case _ =>
    }
  }
}
