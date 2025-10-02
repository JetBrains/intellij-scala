package org.jetbrains.sbt.survey

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.dialog.{BlockBasedFeedbackDialog, SystemDataJsonSerializable}
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import org.jetbrains.sbt.SbtBundle

import kotlinx.datetime.LocalDate

class SeparateMainTestModulesDisabledFeedbackConfig extends InIdeFeedbackSurveyConfig {

  override def getSurveyId: String = "sbt_separate_module_for_main_test_sources_survey"

  override def getLastDayOfFeedbackCollection: LocalDate = new LocalDate(java.time.LocalDate.of(2026, 12, 12))

  override def getRequireIdeEAP: Boolean = false

  override def checkIdeIsSuitable(): Boolean =
    PlatformUtils.isIdeaCommunity() || PlatformUtils.isIdeaUltimate()

  override def checkExtraConditionSatisfied(project: Project): Boolean = true

  override def createNotification(project: Project, b: Boolean): RequestFeedbackNotification =
    new RequestFeedbackNotification(
      "Feedback In IDE", // All in IDE feedback notifications have the same group id
      SbtBundle.message("separate.main.test.modules.feedback.notification.title"),
      SbtBundle.message("separate.main.test.modules.feedback.notification.content")
    )

  override def createFeedbackDialog(project: Project, b: Boolean): BlockBasedFeedbackDialog[? <: SystemDataJsonSerializable] =
    new SeparateMainTestModulesDisabledFeedbackDialog(project, b)

  override def updateStateAfterDialogClosedOk(project: Project): Unit = { }

  override def updateStateAfterNotificationShowed(project: Project): Unit = { }
}
