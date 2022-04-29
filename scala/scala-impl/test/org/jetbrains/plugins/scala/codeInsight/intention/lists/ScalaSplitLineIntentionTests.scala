package org.jetbrains.plugins.scala.codeInsight.intention.lists

import com.intellij.codeInsight.CodeInsightBundle

final class ScalaSplitArgumentsIntentionTest extends ScalaSplitJoinArgumentsIntentionTestBase {
  override val familyName = CodeInsightBundle.message("intention.family.name.split.values")
  override protected val testType = SplitJoinTestType.Split
  override protected val intentionText: String = "Put arguments on separate lines"
}

final class ScalaSplitParametersIntentionTest extends ScalaSplitJoinParametersIntentionTestBase {
  override val familyName = CodeInsightBundle.message("intention.family.name.split.values")
  override protected val testType = SplitJoinTestType.Split
  override protected val intentionText: String = "Put parameters on separate lines"
}
