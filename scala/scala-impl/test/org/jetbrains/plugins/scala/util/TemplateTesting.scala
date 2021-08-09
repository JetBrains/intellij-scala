package org.jetbrains.plugins.scala.util

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import junit.framework.TestCase
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

// mix in this trait if you want to observe myFixture.getLookupElements.
// without it, the template mechanism will just select the first lookup element
trait TemplateTesting extends TestCase { this: ScalaLightCodeInsightFixtureTestAdapter =>

  override def setUp(): Unit = {
    super.setUp()
    TemplateManagerImpl.setTemplateTesting(getFixture.getTestRootDisposable)
  }
}
