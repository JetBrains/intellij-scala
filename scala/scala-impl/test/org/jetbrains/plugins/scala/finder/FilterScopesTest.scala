package org.jetbrains.plugins.scala.finder

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.junit.Assert

class FilterScopesTest extends SimpleTestCase {
  private implicit def getProject: Project = fixture.getProject

  def testEqualsAndHashCode(): Unit = {
    val delegate = GlobalSearchScope.projectScope(getProject)

    val scalaFilterScope1 = ScalaFilterScope(delegate)
    val scalaFilterScope2 = ScalaFilterScope(delegate)

    val sourceFilterScope1 = SourceFilterScope(delegate)
    val sourceFilterScope2 = SourceFilterScope(delegate)

    val resolveFilterScope1 = ResolveFilterScope(delegate)
    val resolveFilterScope2 = ResolveFilterScope(delegate)

    checkEqualsAndHashcode(scalaFilterScope1, scalaFilterScope2)
    checkEqualsAndHashcode(sourceFilterScope1, sourceFilterScope2)
    checkEqualsAndHashcode(resolveFilterScope1, resolveFilterScope2)
  }

  private def checkEqualsAndHashcode(scope1: FilterScope, scope2: FilterScope): Unit = {
    Assert.assertEquals("Scopes should be equal", scope1, scope2)
    Assert.assertEquals("Hashcodes should be equal", scope1.hashCode(), scope2.hashCode())
  }
}
