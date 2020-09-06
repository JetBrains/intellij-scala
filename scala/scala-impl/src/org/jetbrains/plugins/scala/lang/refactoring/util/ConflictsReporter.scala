package org.jetbrains.plugins.scala.lang.refactoring.util

import java.awt.Point

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.{Balloon, JBPopupFactory}
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.extensions.invokeLater

/**
  * @author Alexander Podkhalyuzin
  */
trait ConflictsReporter {
  def reportConflicts(project: Project, conflicts: Seq[(PsiElement, String)]): Boolean
}

trait EmptyConflictsReporter extends ConflictsReporter {
  override def reportConflicts(project: Project, conflicts: Seq[(PsiElement, String)]) = false
}

trait DialogConflictsReporter extends ConflictsReporter {
  override def reportConflicts(project: Project, conflicts: Seq[(PsiElement, String)]): Boolean = {
    val result = MultiMap.createSet[PsiElement, String]()
    conflicts.foreach {
      case (element, message) => result.putValue(element, message)
    }

    val conflictsDialog = new ConflictsDialog(project, result, null, true, false)
    conflictsDialog.show()
    conflictsDialog.isOK
  }
}

class BalloonConflictsReporter(editor: Editor) extends ConflictsReporter {

  override def reportConflicts(project: Project, conflicts: Seq[(PsiElement, String)]): Boolean = {
    val messages = conflicts.map(_._2).toSet
    createWarningBalloon(messages.mkString("\n"))
    true //this means that we do nothing, only show balloon
  }

  private def createWarningBalloon(message: String): Unit = {
    invokeLater {
      val popupFactory = JBPopupFactory.getInstance
      val bestLocation = popupFactory.guessBestPopupLocation(editor)
      val screenPoint: Point = bestLocation.getScreenPoint
      val y: Int = screenPoint.y - editor.getLineHeight * 2
      //noinspection ReferencePassedToNls
      val builder = popupFactory.createHtmlTextBalloonBuilder(message, null, MessageType.WARNING.getPopupBackground, null)
      val balloon: Balloon = builder.setFadeoutTime(-1).setShowCallout(false).createBalloon
      balloon.show(new RelativePoint(new Point(screenPoint.x, y)), Balloon.Position.above)
    }
  }
}