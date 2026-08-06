package org.jetbrains.plugins.scala.annotator

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
abstract class ScalaHighlightingTestBase extends ScalaFixtureTestCase with ScalaHighlightingTestLike {

  override def getFixture: CodeInsightTestFixture = myFixture
}


