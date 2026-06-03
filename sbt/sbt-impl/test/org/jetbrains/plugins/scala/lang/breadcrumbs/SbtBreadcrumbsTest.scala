package org.jetbrains.plugins.scala.lang.breadcrumbs

import com.intellij.lang.Language
import org.jetbrains.sbt.language.SbtLanguage

final class SbtBreadcrumbsTest extends ScalaBreadcrumbsTestBase {
  override protected def breadcrumbsDefaultVisibilityTestLanguages: Seq[Language] = Seq(SbtLanguage.INSTANCE)
}
