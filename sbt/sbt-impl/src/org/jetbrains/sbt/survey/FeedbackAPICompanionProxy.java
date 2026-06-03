package org.jetbrains.sbt.survey;

import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData;

public class FeedbackAPICompanionProxy {
    public static CommonFeedbackSystemData currentData = CommonFeedbackSystemData.Companion.getCurrentData();
}
