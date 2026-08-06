package org.jetbrains.sbt.survey

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.impl.state.DontShowAgainFeedbackService
import com.intellij.platform.feedback.{FeedbackSurvey, FeedbackSurveyType, InIdeFeedbackSurveyType}
import org.jetbrains.plugins.scala.extensions.executeOnPooledThread

class SeparateMainTestModulesDisabledFeedback extends FeedbackSurvey {

  override def getFeedbackSurveyType: FeedbackSurveyType[SeparateMainTestModulesDisabledFeedbackConfig] =
    new InIdeFeedbackSurveyType(new SeparateMainTestModulesDisabledFeedbackConfig)
}

object SeparateMainTestModulesDisabledFeedback {
  private val Feedback = new SeparateMainTestModulesDisabledFeedback()

  def showNotification(project: Project): Unit =
    executeOnPooledThread {
      val shouldShow = Feedback.isSuitableToShow$intellij_platform_feedback(project) && canShowFeedbackNotification()
      if (shouldShow) {
        Feedback.showNotification(project, false)
      }
    }

  /**
   * Checks if feedback notifications are allowed based on IDE version and platform registry.
   *
   * @note it's copied from [[com.intellij.platform.feedback.impl.OnDemandFeedbackResolver.Companion#canShowFeedbackNotification]] and the
   * same logic is present in [[com.intellij.platform.feedback.impl.IdleFeedbackResolver#showFeedbackNotification$intellij_platform_feedback]]
   */
  private def canShowFeedbackNotification(): Boolean =
    DontShowAgainFeedbackService.checkIsAllowedToShowFeedback() && Registry.`is`("platform.feedback", true)
}
