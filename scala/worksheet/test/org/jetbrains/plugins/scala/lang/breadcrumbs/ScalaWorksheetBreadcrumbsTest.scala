package org.jetbrains.plugins.scala.lang.breadcrumbs

import com.intellij.lang.Language
import org.jetbrains.plugins.scala.worksheet.{WorksheetLanguage, WorksheetLanguage3}

final class ScalaWorksheetBreadcrumbsTest extends ScalaBreadcrumbsTestBase {
  override protected def breadcrumbsDefaultVisibilityTestLanguages: Seq[Language] = Seq(
    WorksheetLanguage.INSTANCE,
    WorksheetLanguage3.INSTANCE
  )
}
