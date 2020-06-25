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

    val file = fixture.addFileToProject("worksheet.sc", "2 + 2")
    val worksheetResolveFilterScope1 = WorksheetResolveFilterScope(delegate, file.getVirtualFile)
    val worksheetResolveFilterScope2 = WorksheetResolveFilterScope(delegate, file.getVirtualFile)

    checkEqualsAndHashcode(scalaFilterScope1, scalaFilterScope2)
    checkEqualsAndHashcode(sourceFilterScope1, sourceFilterScope2)
    checkEqualsAndHashcode(resolveFilterScope1, resolveFilterScope2)
    checkEqualsAndHashcode(worksheetResolveFilterScope1, worksheetResolveFilterScope2)
  }

  def testNotEqualsAndHashCode(): Unit = {
    val worksheetFile1 = fixture.addFileToProject("worksheet1.sc", "2 + 2")
    val worksheetFile2 = fixture.addFileToProject("worksheet2.sc", "2 + 2")
    val scalaFile = fixture.addFileToProject("A.scala", "class A {}")
    val delegate1 = GlobalSearchScope.projectScope(getProject)
    val delegate2 = GlobalSearchScope.fileScope(scalaFile)

    val uniqueScopes = Seq(
      ScalaFilterScope(delegate1),
      ScalaFilterScope(delegate2),
      SourceFilterScope(delegate1),
      SourceFilterScope(delegate2),
      ResolveFilterScope(delegate1),
      ResolveFilterScope(delegate2),
      WorksheetResolveFilterScope(delegate1, worksheetFile1.getVirtualFile),
      WorksheetResolveFilterScope(delegate1, worksheetFile2.getVirtualFile),
      WorksheetResolveFilterScope(delegate2, worksheetFile1.getVirtualFile),
      WorksheetResolveFilterScope(delegate2, worksheetFile2.getVirtualFile),
    )
    for {
      i <- uniqueScopes.indices
      j <- i + 1 until uniqueScopes.length
    } checkNotEqualsAndHashcode(uniqueScopes(i), uniqueScopes(j))
  }

  private def checkEqualsAndHashcode(scope1: FilterScope, scope2: FilterScope): Unit = {
    Assert.assertEquals("Scopes should be equal", scope1, scope2)
    Assert.assertEquals("Hashcodes should be equal", scope1.hashCode(), scope2.hashCode())
  }

  private def checkNotEqualsAndHashcode(scope1: FilterScope, scope2: FilterScope): Unit = {
    Assert.assertNotEquals("Scopes should not be equal", scope1, scope2)
    Assert.assertNotEquals("Hashcodes should not be equal", scope1.hashCode(), scope2.hashCode())
  }
}
