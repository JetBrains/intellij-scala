package org.jetbrains.plugins.scala.codeInsight.intention.lists

import com.intellij.codeInsight.CodeInsightBundle

sealed trait ScalaSplitLineIntentionTestBase {
  self: ScalaSplitJoinLineIntentionTestBase =>
  override final val familyName = CodeInsightBundle.message("intention.family.name.split.values")
  override protected final val testType = SplitJoinTestType.Split
}

final class ScalaSplitArgumentsIntentionTest
  extends ScalaSplitJoinArgumentsIntentionTestBase
    with ScalaSplitLineIntentionTestBase {
  override protected val intentionText: String = "Put arguments on separate lines"
}

final class ScalaSplitParametersIntentionTest
  extends ScalaSplitJoinParametersIntentionTestBase
    with ScalaSplitLineIntentionTestBase {
  override protected val intentionText: String = "Put parameters on separate lines"
}

final class ScalaSplitTupleTypesIntentionTest
  extends ScalaSplitJoinTupleTypesIntentionTestBase
    with ScalaSplitLineIntentionTestBase {
  override protected val intentionText: String = "Put tuple type elements on separate lines"
}

final class ScalaSplitTuplesIntentionTest
  extends ScalaSplitJoinTuplesIntentionTestBase
    with ScalaSplitLineIntentionTestBase {
  override protected val intentionText: String = "Put tuple elements on separate lines"
}

final class ScalaSplitTypeArgumentsIntentionTest
  extends ScalaSplitJoinTypeArgumentsIntentionTestBase
    with ScalaSplitLineIntentionTestBase {
  override protected val intentionText: String = "Put type arguments on separate lines"
}

final class ScalaSplitTypeParametersIntentionTest
  extends ScalaSplitJoinTypeParametersIntentionTestBase
    with ScalaSplitLineIntentionTestBase {
  override protected val intentionText: String = "Put type parameters on separate lines"
}
