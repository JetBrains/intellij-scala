package org.jetbrains.plugins.scala.project.settings

import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.scala.compiler.data.{CompileOrder, ScalaCompilerSettingsState, ScalaCompilerSettingsStateBuilder}
import org.jetbrains.plugins.scala.project.settings.ReflectionTestUtils.assertEqualsWithFieldValuesDiff
import org.junit.Assert.{assertEquals, assertNotEquals}

/**
 * @see [[org.jetbrains.plugins.scala.project.settings.ScalaCompilerSettings]]
 * @see [[org.jetbrains.plugins.scala.compiler.data.ScalaCompilerSettingsState]]
 */
class ScalaCompilerSettingsTest extends LightPlatformTestCase {

  def testScalaCompilerSettings_ToState_ShouldReturnSameInstanceAsWasPassedTo_FromState(): Unit = {
    val stateWithNonDefaultFieldValues = ReflectionTestUtils.createInstanceWithNonDefaultValues(classOf[ScalaCompilerSettingsState])

    val settings = ScalaCompilerSettings.fromState(stateWithNonDefaultFieldValues)
    val stateAfterConversions = settings.toState

    assertEqualsWithFieldValuesDiff(
      "Calling ScalaCompilerSettings.fromState and then ScalaCompilerSettings.toState should return instance  which is equal to the original state",
      stateWithNonDefaultFieldValues,
      stateAfterConversions
    )
  }

  def testScalaCompilerSettingsPanel_GetState_ShouldReturnSameInstanceAsWasPassedTo_SetState(): Unit = {
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

  def testScalaCompilerSettingsStateBuilder_StateFromOptions_From_GetOptionsAsStrings(): Unit = {
    val stateBefore = ReflectionTestUtils.createInstanceWithNonDefaultValues(classOf[ScalaCompilerSettingsState])

    val stateWithDefaultValues = new ScalaCompilerSettingsState
    //These settings are not represented in scala compiler options, so resetting them to defaults
    stateBefore.nameHashing = stateWithDefaultValues.nameHashing
    stateBefore.recompileOnMacroDef = stateWithDefaultValues.recompileOnMacroDef
    stateBefore.transitiveStep = stateWithDefaultValues.transitiveStep
    stateBefore.recompileAllFraction = stateWithDefaultValues.recompileAllFraction

    val optionsStrings = ScalaCompilerSettingsStateBuilder.getOptionsAsStrings(stateBefore, forScala3Compiler = false, canonisePath = false)
    val stateAfter = ScalaCompilerSettingsStateBuilder.stateFromOptions(optionsStrings, stateBefore.compileOrder)
    assertEqualsWithFieldValuesDiff(
      "Calling ScalaCompilerSettingsStateBuilder.getOptionsAsStrings and then ScalaCompilerSettingsStateBuilder.stateFromOptions should return instance which is equal to the original state",
      stateBefore,
      stateAfter
    )
  }

  def testScalaCompilerSettingsStateBuilder_StateFromOptions_SplitMultipleOptionValues(): Unit = {
    val options = Seq(
      "-language:dynamics,postfixOps,reflectiveCalls,implicitConversions,higherKinds,existentials,experimental.macros,someUnknownFeature",
      "-Xplugin:plugin path 1;plugin path 2;plugin path 3"
    )
    val state = ScalaCompilerSettingsStateBuilder.stateFromOptions(options, CompileOrder.Mixed)
    assertEquals(
      Seq(
        "-language:dynamics",
        "-language:postfixOps",
        "-language:reflectiveCalls",
        "-language:implicitConversions",
        "-language:higherKinds",
        "-language:existentials",
        "-language:experimental.macros",
        "-g:vars",
        "-Xplugin:plugin path 1",
        "-Xplugin:plugin path 2",
        "-Xplugin:plugin path 3",
        "-language:someUnknownFeature"
      ),
      ScalaCompilerSettingsStateBuilder.getOptionsAsStrings(state, forScala3Compiler = false, canonisePath = false)
    )
  }
}