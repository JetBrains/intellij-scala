package org.jetbrains.plugins.scala.lang.overrideImplement

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil.invokeOverrideImplement
import org.jetbrains.plugins.scala.overrideImplement.{ClassMember, ScalaOIUtil}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings
import org.junit.Assert.assertEquals

import scala.reflect.ClassTag

abstract class ScalaOverrideImplementTestBase extends ScalaLightCodeInsightFixtureTestCase {

  protected def runTest(
    methodName: String,
    fileText: String,
    expectedText: String,
    isImplement: Boolean,
    settings: ScalaCodeStyleSettings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject)),
    copyScalaDoc: Boolean = false,
    fileName: String = "dummy.scala"
  ): Unit = runTest(
    Some(methodName),
    fileText,
    expectedText,
    isImplement,
    settings,
    copyScalaDoc,
    fileName
  )

  protected def runImplementAllTest(
    fileText: String,
    expectedText: String,
    copyScalaDoc: Boolean = false
  ): Unit = {
    val isImplement = true
    val fileName: String = "dummy.scala"
    val settings = TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProject))
    runTest(
      None,
      fileText,
      expectedText,
      isImplement,
      settings,
      copyScalaDoc,
      fileName
    )
  }

  protected def runTest(
    methodName: Option[String],
    fileText: String,
    expectedText: String,
    isImplement: Boolean,
    settings: ScalaCodeStyleSettings,
    copyScalaDoc: Boolean,
    fileName: String
  ): Unit = {
    implicit val project: Project = getProject

    myFixture.configureByText(fileName, convertLineSeparators(fileText))

    val oldSettings = prepareSettings(settings)
    val defaultProfile = ScalaCompilerConfiguration.instanceIn(project).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "kind-projector"
    )
    defaultProfile.setSettings(newSettings)

    implicit val editor: Editor = getEditor
    ScalaApplicationSettings.getInstance.COPY_SCALADOC = copyScalaDoc

    invokeOverrideImplement(getFile, isImplement, methodName)

    rollbackSettings(oldSettings)
    myFixture.checkResult(convertLineSeparators(expectedText))
  }

  protected def assertTemplateDefinitionSelectedForAction(
    fileText: String,
    expectedName: String
  ): Unit = {
    val testName = getTestName(false) + ".scala"
    myFixture.configureByText(testName, fileText.withNormalizedSeparator)

    val actualDef = ScalaOIUtil.getTemplateDefinitionForOverrideImplementAction(getFile, getEditor)
    val actualDefName = actualDef.map(_.name).orNull
    assertEquals("Wrong definition was selected for override/implement action", expectedName, actualDefName)
  }

  protected def assertMembersPresentableText[T <: ClassMember : ClassTag](
    fileText: String,
    className: String,
    filterMethods: T => Boolean,
    expectedMembersConcatenatedText: String
  ): Unit = {
    configureFromFileText(fileText.withNormalizedSeparator)

    val clazz: ScTemplateDefinition = getFile.asInstanceOf[ScalaFile]
      .elements.collectFirst { case c: ScTemplateDefinition if c.name == className => c }
      .getOrElse(throw new AssertionError(s"Can't find definition with name $className"))

    val members = ScalaOIUtil.getAllMembersToOverride(clazz)
    val membersFilteredAndSorted = members
      .filterByType[T].filter(filterMethods)
      .sortBy(_.getElement.getNode.getStartOffset)

    assertEquals(
      expectedMembersConcatenatedText.trim,
      membersFilteredAndSorted.map(_.getText).mkString("\n")
    )
  }

  protected def prepareSettings(newSettings: ScalaCodeStyleSettings)(implicit project: Project): ScalaCodeStyleSettings = {
    val oldSettings = ScalaCodeStyleSettings.getInstance(project).clone().asInstanceOf[ScalaCodeStyleSettings]
    TypeAnnotationSettings.set(project, newSettings)
    oldSettings
  }

  protected def rollbackSettings(oldSettings: ScalaCodeStyleSettings)(implicit project: Project): Unit =
    TypeAnnotationSettings.set(project, oldSettings)
}