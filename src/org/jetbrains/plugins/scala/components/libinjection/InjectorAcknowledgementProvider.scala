package org.jetbrains.plugins.scala.components.libinjection

import javax.swing.event.HyperlinkEvent

import com.intellij.notification.{Notification, NotificationGroup, NotificationListener, NotificationType}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project._

/**
  * Created by mucianm on 16.03.16.
  */
trait InjectorAcknowledgementProvider {

  type ManifestToDescriptors = LibraryInjectorLoader#ManifestToDescriptors

  def askGlobalInjectorEnable(acceptCallback: => Any)

  def showReviewDialogAndFilter(candidates: ManifestToDescriptors): (ManifestToDescriptors, ManifestToDescriptors)

}

// enable injector loading in tests, accept all injectors
class TestAcknowledgementProvider extends InjectorAcknowledgementProvider {
  override def askGlobalInjectorEnable(acceptCallback: => Any) = acceptCallback
  override def showReviewDialogAndFilter(candidates: ManifestToDescriptors) = (candidates, Seq.empty)
}

class UIAcknowledgementProvider(private val GROUP: NotificationGroup,
                                private val project: Project)(implicit val LOG: Logger)
  extends InjectorAcknowledgementProvider
{
  override def askGlobalInjectorEnable(acceptCallback: => Any) = {
    val message = s"Some of your project's libraries have IDEA support features.</p>Would you like to load them?" +
      s"""<p/><a href="Yes">Yes</a> """ +
      s"""<a href="No">No</a>"""
    val listener = new NotificationListener {
      override def hyperlinkUpdate(notification: Notification, event: HyperlinkEvent): Unit = {
        notification.expire()
        if (event.getDescription == "Yes")
          acceptCallback
      }
    }
    GROUP.createNotification("IDEA Extensions", message, NotificationType.INFORMATION, listener).notify(project)
  }

  override def showReviewDialogAndFilter(candidates: ManifestToDescriptors) = {
   candidates.partition { candidate =>
      val dialog = new InjectorReviewDialog(project, candidate, LOG)
      dialog.showAndGet()
    }
  }
}