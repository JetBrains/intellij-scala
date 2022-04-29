package org.jetbrains.plugins.scala.codeInsight.intention.lists

import com.intellij.codeInsight.CodeInsightBundle

final class ScalaJoinArgumentsIntentionTest extends ScalaSplitJoinArgumentsIntentionTestBase {
  override val familyName = CodeInsightBundle.message("intention.family.name.join.values")
  override protected val testType = SplitJoinTestType.Join
  override protected val intentionText: String = "Put arguments on one line"
}

final class ScalaJoinParametersIntentionTest extends ScalaSplitJoinParametersIntentionTestBase {
  override val familyName = CodeInsightBundle.message("intention.family.name.join.values")
  override protected val testType = SplitJoinTestType.Join
  override protected val intentionText: String = "Put parameters on one line"
}
