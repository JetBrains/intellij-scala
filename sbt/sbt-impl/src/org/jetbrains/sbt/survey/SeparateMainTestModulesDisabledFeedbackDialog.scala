package org.jetbrains.sbt.survey

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.uiBlocks.{DescriptionBlock, FeedbackBlock, RatingBlock, TextAreaBlock, TopLabelBlock}
import com.intellij.platform.feedback.dialog.{BaseFeedbackSystemInfoDialogKt, BlockBasedFeedbackDialogWithEmail, CommonFeedbackSystemData}
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import com.intellij.util.JavaCoroutines
import org.jetbrains.sbt.SbtBundle

import java.util.{List => JList}
import kotlin.coroutines.Continuation
import kotlinx.serialization.json.JsonObject

class SeparateMainTestModulesDisabledFeedbackDialog(project: Project, forTest: Boolean)
  extends BlockBasedFeedbackDialogWithEmail[CommonFeedbackSystemData](project, forTest) {

  override def getMyFeedbackReportId: String = "sbt_separate_module_for_main_test_sources_survey"
  override def getMyTitle: String = "Feedback"

  override def getZendeskTicketTitle: String = "Feedback: Separate Main/Test Modules"
  override def getZendeskFeedbackType: String = "Feedback: Separate Main/Test Modules"

  private lazy val mySystemInfoData: CommonFeedbackSystemData =
    FeedbackAPICompanionProxy.currentData

  override protected def computeSystemInfoData(cont: Continuation[? >: CommonFeedbackSystemData]): AnyRef =
    JavaCoroutines.suspendJava[CommonFeedbackSystemData](continuation => {
      continuation.resume(mySystemInfoData)
    }, cont)

  override protected def showFeedbackSystemInfoDialog(data: CommonFeedbackSystemData): Unit = {
    BaseFeedbackSystemInfoDialogKt.showFeedbackSystemInfoDialog(project, data, _ => kotlin.Unit.INSTANCE)
  }

  override val getMyBlocks: JList[FeedbackBlock] = JList.of(
    new TopLabelBlock(SbtBundle.message("separate.main.test.modules.feedback.dialog.title")),
    new DescriptionBlock(SbtBundle.message("separate.main.test.modules.feedback.dialog.subtitle")),
    new RatingBlock(SbtBundle.message("separate.main.test.modules.feedback.dialog.rating"), "rating"),
    new TextAreaBlock(SbtBundle.message("separate.main.test.modules.feedback.dialog.textbox"), "tell_us_more")
  )

  override def shouldAutoCloseZendeskTicket(): Boolean =
    getEmailBlockWithAgreement.getEmailAddressIfSpecified.isBlank

  override def computeZendeskTicketTags(collectedData: JsonObject): JList[String] =
    JList.of(getMyFeedbackReportId)

  {
    // It's required to initialize the dialog
    init()
  }

  override def showThanksNotification(): Unit =
    new ThanksForFeedbackNotification(
      SbtBundle.message("separate.main.test.modules.feedback.dialog.thank.you.notification.title"),
      SbtBundle.message("separate.main.test.modules.feedback.dialog.thank.you.notification.desc"),
    ).notify(getMyProject)
}