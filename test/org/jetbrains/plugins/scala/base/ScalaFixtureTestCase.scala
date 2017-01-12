package org.jetbrains.plugins.scala
package base

import com.intellij.testFramework.fixtures.{CodeInsightFixtureTestCase, CodeInsightTestFixture}
import org.jetbrains.plugins.scala.util.TestUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 03.08.2009
 */
abstract class ScalaFixtureTestCase extends CodeInsightFixtureTestCase with TestFixtureProvider {

  override final def fixture: CodeInsightTestFixture = myFixture

  protected override val rootPath: String = TestUtils.getTestDataPath + "/"

  override protected def setUp(): Unit = {
    super.setUp()
    initFixture()
  }

  override def tearDown(): Unit = {
    cleanFixture()
    super.tearDown()
  }
}