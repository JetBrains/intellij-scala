package org.jetbrains.plugins.scala
package lang.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.MoveDestination
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
 * Nikolay.Tropin
 * 11/29/13
 */
class ScalaMoveClassesOrPackagesProcessor(project: Project,
                                          elements: Array[PsiElement],
                                          moveDestination: MoveDestination,
                                          searchInComments: Boolean,
                                          searchInNonJavaFiles: Boolean,
                                          moveCallback: MoveCallback) extends
{
  private val expandedElements =
    if (ScalaApplicationSettings.getInstance().MOVE_COMPANION)
      elements.flatMap {
        case td: ScTypeDefinition => td :: ScalaPsiUtil.getBaseCompanionModule(td).toList
        case e => List(e)
      }
    else elements

} with MoveClassesOrPackagesProcessor(project, expandedElements, moveDestination, searchInComments, searchInNonJavaFiles, moveCallback){
  extensions.inWriteAction {
    expandedElements.foreach(c =>
      ScalaMoveUtil.saveMoveDestination(c, moveDestination.getTargetDirectory(c.getContainingFile)))
  }

}
