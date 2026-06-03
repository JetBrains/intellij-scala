package org.jetbrains.plugins.scala.codeInsight.intention.lists

import com.intellij.codeInsight.CodeInsightBundle

sealed trait ScalaJoinLineIntentionTestBase {
  self: ScalaSplitJoinLineIntentionTestBase =>
  override final val familyName = CodeInsightBundle.message("intention.family.name.join.values")
  override protected final val testType = SplitJoinTestType.Join
}

final class ScalaJoinArgumentsIntentionTest
  extends ScalaSplitJoinArgumentsIntentionTestBase
    with ScalaJoinLineIntentionTestBase {
  override protected val intentionText: String = "Put arguments on one line"
}

final class ScalaJoinParametersIntentionTest
  extends ScalaSplitJoinParametersIntentionTestBase
    with ScalaJoinLineIntentionTestBase {
  override protected val intentionText: String = "Put parameters on one line"
}

final class ScalaJoinTupleTypesIntentionTest
  extends ScalaSplitJoinTupleTypesIntentionTestBase
    with ScalaJoinLineIntentionTestBase {
  override protected val intentionText = "Put tuple type elements on one line"
}

final class ScalaJoinTuplesIntentionTest
  extends ScalaSplitJoinTuplesIntentionTestBase
    with ScalaJoinLineIntentionTestBase {
  override protected val intentionText = "Put tuple elements on one line"
}

final class ScalaJoinTypeArgumentsIntentionTest
  extends ScalaSplitJoinTypeArgumentsIntentionTestBase
    with ScalaJoinLineIntentionTestBase {
  override protected val intentionText: String = "Put type arguments on one line"
}

final class ScalaJoinTypeParametersIntentionTest
  extends ScalaSplitJoinTypeParametersIntentionTestBase
    with ScalaJoinLineIntentionTestBase {
  override protected val intentionText: String = "Put type parameters on one line"
}
