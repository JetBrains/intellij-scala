package org.jetbrains.plugins.scala.project.settings

import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.scala.compiler.data.ScalaCompilerSettingsState
import org.jetbrains.plugins.scala.project.settings.ReflectionTestUtils.assertEqualsWithFieldValuesDiff
import org.junit.Assert.assertNotEquals

/**
 * @see [[org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings]]
 * @see [[org.jetbrains.plugins.scala.compiler.data.ScalaCompilerSettingsState]]
 */
class ScalaCompilerSettingsTest extends LightPlatformTestCase {

  def testScalaCompilerSettings_ToStateShouldReturnSameInstanceAsWasPassedToFromState(): Unit = {
    val stateWithNonDefaultFieldValues = ReflectionTestUtils.createInstanceWithNonDefaultValues(classOf[ScalaCompilerSettingsState])

    val settings = ScalaCompilerSettings.fromState(stateWithNonDefaultFieldValues)
    val stateAfterConversions = settings.toState

    assertEqualsWithFieldValuesDiff(
      "Calling ScalaCompilerSettings.fromState and then ScalaCompilerSettings.toState should return instance  which is equal to the original state",
      stateWithNonDefaultFieldValues,
      stateAfterConversions
    )
  }

  def testScalaCompilerSettingsPanel_GetStateShouldReturnSameInstanceAsWasPassedToSetState(): Unit = {
    val stateWithNonDefaultFieldValues = ReflectionTestUtils.createInstanceWithNonDefaultValues(classOf[ScalaCompilerSettingsState])

    val panel = new ScalaCompilerSettingsPanel()
    panel.setState(stateWithNonDefaultFieldValues)
    val stateAfterConversions = panel.getState

    assertEqualsWithFieldValuesDiff(
      "Calling ScalaCompilerSettingsPanel.setState and then ScalaCompilerSettingsPanel.getState should return instance  which is equal to the original state",
      stateWithNonDefaultFieldValues,
      stateAfterConversions
    )
  }

  def testScalaCompilerSettingsState_EqualsAndHashcodeShouldUseEveryField(): Unit = {
    val clazz = classOf[ScalaCompilerSettingsState]

    val defaultConstructor = clazz.getDeclaredConstructor()
    val instanceWithDefaultValues = defaultConstructor.newInstance()
    val fields = ReflectionTestUtils.getPublicFields(clazz)

    for (field <- fields) {
      val instanceWithModifiedSingleField = defaultConstructor.newInstance()
      ReflectionTestUtils.setNonDefaultFieldValue(instanceWithModifiedSingleField, field)

      assertNotEquals(
        s"Changing default value of field `${field.getName}` should change equality",
        instanceWithDefaultValues,
        instanceWithModifiedSingleField
      )

      assertNotEquals(
        s"Changing default value of field `${field.getName}` should change hash code",
        instanceWithDefaultValues.hashCode,
        instanceWithModifiedSingleField.hashCode
      )
    }
  }

}