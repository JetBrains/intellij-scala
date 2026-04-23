package org.jetbrains.plugins.scala.lang.breadcrumbs

import com.intellij.ide.ui.UISettings
import com.intellij.lang.Language
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.xml.breadcrumbs.BreadcrumbsUtilEx
import junitparams.naming.TestCaseName
import junitparams.{JUnitParamsRunner, Parameters}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.RevertableChange
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

import scala.annotation.unused

@RunWith(classOf[JUnitParamsRunner])
abstract class ScalaBreadcrumbsTestBase extends ScalaLightCodeInsightFixtureTestCase {
  @Test
  @Parameters(method = "breadcrumbsDefaultVisibilityParams")
  @TestCaseName("{method}[{0}, showingMembersInNavBar = {1}]")
  def testBreadcrumbsDefaultVisibility(lang: Language, showMembersInNavBar: Boolean): Unit =
    RevertableChange.withModifiedSetting(UISettings.getInstance())(showMembersInNavBar)(
      _.getShowMembersInNavigationBar,
      _.setShowMembersInNavigationBar(_)
    ).run {
      UISettings.getInstance().fireUISettingsChanged()
      EditorSettingsExternalizable.getInstance().resetDefaultBreadcrumbVisibility()

      val expected = !showMembersInNavBar
      val actual = BreadcrumbsUtilEx.isBreadcrumbsShownFor(lang)
      assertEquals(expected, actual)
    }

  protected def breadcrumbsDefaultVisibilityTestLanguages: Seq[Language]

  @unused("used reflectively by the @Parameters annotation")
  private def breadcrumbsDefaultVisibilityParams: Array[Array[Any]] = {
    val params = for {
      lang <- breadcrumbsDefaultVisibilityTestLanguages
      showMembersInNavBar <- Seq(true, false)
    } yield Array(lang, showMembersInNavBar)
    params.toArray
  }
}

// TODO: add actual breadcrumbs tests using doTest(...)
final class ScalaBreadcrumbsTest extends ScalaBreadcrumbsTestBase {
  override protected def breadcrumbsDefaultVisibilityTestLanguages: Seq[Language] = Seq(
    ScalaLanguage.INSTANCE,
    Scala3Language.INSTANCE
  )
}
