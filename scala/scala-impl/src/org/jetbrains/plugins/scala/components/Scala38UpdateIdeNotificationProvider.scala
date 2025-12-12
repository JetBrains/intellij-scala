package org.jetbrains.plugins.scala.components

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.{EditorNotificationPanel, EditorNotificationProvider, InlineBanner}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.{ProjectExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion}
import org.jetbrains.sbt.language.SbtFile

import javax.swing.JComponent

final class Scala38UpdateIdeNotificationProvider extends EditorNotificationProvider {
  import Scala38UpdateIdeNotificationProvider.{canBeShownAgain, setDoNotShowAgain}

  override def collectNotificationData(
    project: Project,
    file: VirtualFile
  ): java.util.function.Function[_ >: FileEditor, _ <: JComponent] = {
    if (!canBeShownAgain(project)) return null

    highestScalaVersion(project) match {
      case Some(version) if isScala38orLater(version) =>
        PsiManager.getInstance(project).findFile(file) match {
          case _: ScalaFile | _: SbtFile =>
            (_: FileEditor) => createPanel(project, version)
          case _ =>
            null
        }
      case _ =>
        null
    }
  }

  private def createPanel(project: Project, version: ScalaVersion): JComponent = {
    val panel = new InlineBanner(
      ScalaBundle.message("scala.3.8.support.update.ide.editor.notification.short.text"),
      EditorNotificationPanel.Status.Warning
    )

    def addDontShowAgainAction(): Unit = {
      panel.addAction(
        ScalaBundle.message("scala.3.8.update.ide.editor.notification.do.not.show.again.label"),
        () => {
          setDoNotShowAgain(project)
          panel.close()
        }
      )
    }

    panel.addAction(ScalaBundle.message("scala.3.8.update.ide.editor.notification.more.label"), () => {
      panel.setMessage(ScalaBundle.message("scala.3.8.support.update.ide.editor.notification.long.text", version.minor))
      //noinspection ApiStatus
      panel.removeAllActions()
      addDontShowAgainAction()
    })
    addDontShowAgainAction()
    panel
  }

  private def highestScalaVersion(project: Project): Option[ScalaVersion] =
    project.allScalaVersions.maxOption

  private def isScala38orLater(scalaVersion: ScalaVersion): Boolean =
    scalaVersion.languageLevel >= ScalaLanguageLevel.Scala_3_8
}

private object Scala38UpdateIdeNotificationProvider {
  private final val DoNotShowAgain: Key[Boolean] = Key.create("scala3.8.update.ide.disclaimer.editor.notification.do.not.show.again")

  private def canBeShownAgain(project: Project): Boolean = !DoNotShowAgain.get(project, false)

  private def setDoNotShowAgain(project: Project): Unit = {
    DoNotShowAgain.set(project, true)
  }
}
