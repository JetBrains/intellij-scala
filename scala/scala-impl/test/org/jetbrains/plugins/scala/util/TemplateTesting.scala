package org.jetbrains.plugins.scala.util

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase

// mix in this trait if you want to observe myFixture.getLookupElements.
// without it, the template mechanism will just select the first lookup element
trait TemplateTesting { self: TestCase =>
  private val templateTestingDisposable = Disposer.newDisposable()

  override def setUp(): Unit = {
    self.setUp()
    TemplateManagerImpl.setTemplateTesting(templateTestingDisposable)
  }

  override def tearDown(): Unit = {
    templateTestingDisposable.dispose()
    self.tearDown()
  }
}
