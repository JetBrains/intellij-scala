package org.jetbrains.plugins.scala.util

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import junit.framework.TestCase
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

// mix in this trait if you want to observe myFixture.getLookupElements.
// without it, the template mechanism will just select the first lookup element
trait TemplateTesting { self: TestCase with ScalaLightCodeInsightFixtureTestAdapter =>

  override def setUp(): Unit = {
    self.setUp()
    TemplateManagerImpl.setTemplateTesting(self.getFixture.getTestRootDisposable)
  }
}
