package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.junit.Assert

/**
  * Created by kate
  * on 1/29/16
  */
abstract class ScalaTestCompletionWithOrder extends ScalaCodeInsightTestBase {
  def checkResultWithOrder(expected: Array[AnyRef], fileText: String): Unit = {
    configureFromFileTextAdapter("dummy.scala", fileText.stripMargin('|').replaceAll("\r", "").trim())
    val (activeLookup, _) = complete(2, CompletionType.BASIC)

    val lookupItems = activeLookup.map(_.getLookupString).toArray[AnyRef]
    val expectedLength = math.min(expected.length, lookupItems.length)

    Assert.assertArrayEquals(expected.take(expectedLength), lookupItems.take(expectedLength))
  }
}
