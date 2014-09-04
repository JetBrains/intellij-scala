package org.jetbrains.plugins.scala
package lang.navigation

import com.intellij.ide.util.gotoByName.GotoClassModel2
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter

/**
 * @author Alefas
 * @since 23.12.13
 */
class GoToClassTest extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  protected override def rootPath(): String = baseRootPath() + "navigation/gotoclass/"

  def testTrait() {
    val gotoModel = new GotoClassModel2(getProjectAdapter)
    val length: Int = gotoModel.getElementsByName("GoToClassSimpleTrait", false, "GoToClassSimpleT").length
    assert(length == 1, s"Number of SimpleTraits is $length...")
  }

  def testObject() {
    val gotoModel = new GotoClassModel2(getProjectAdapter)
    val length: Int = gotoModel.getElementsByName("GoToClassSimpleObject$", false, "GoToClassSimpleO").length
    assert(length == 1, s"Number of SimpleObjects$$ is $length...")
    val length2: Int = gotoModel.getElementsByName("GoToClassSimpleObject", false, "GoToClassSimpleO").length
    assert(length2 == 0, s"Number of SimpleObjects is $length2...")
  }
}
