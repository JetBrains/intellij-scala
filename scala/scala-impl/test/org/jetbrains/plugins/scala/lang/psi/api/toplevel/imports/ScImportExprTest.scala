package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt}
import org.junit.Assert.assertEquals

class ScImportExprTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  def testExplicitNamedMembersFromDirectReference(): Unit = {
    configureScalaFromFileText("import fixture.direct")

    assertEquals(Seq("direct" -> "fixture.direct"), explicitNamedMembers)
  }

  def testExplicitNamedMembersFromSelectors(): Unit = {
    configureScalaFromFileText("import fixture.{selected, renamed => alias, hidden => _}")

    assertEquals(
      Seq(
        "selected" -> "selected",
        "alias" -> "renamed"
      ),
      explicitNamedMembers
    )
  }

  def testExplicitNamedMembersFromWildcardImport(): Unit = {
    configureScalaFromFileText("import fixture.*")

    assertEquals(Seq.empty, explicitNamedMembers)
  }

  def testExplicitNamedMembersExcludeDirectGivenImport(): Unit = {
    configureScalaFromFileText("import fixture.given")

    assertEquals(Seq.empty, explicitNamedMembers)
  }

  def testExplicitNamedMembersExcludeGivenSelector(): Unit = {
    configureScalaFromFileText("import fixture.{given Ordering[String]}")

    assertEquals(Seq.empty, explicitNamedMembers)
  }

  def testExplicitNamedMembersIgnoreWildcardSelectors(): Unit = {
    configureScalaFromFileText("import fixture.{selected, *}")

    assertEquals(Seq("selected" -> "selected"), explicitNamedMembers)
  }

  private def explicitNamedMembers: Seq[(String, String)] = {
    val importExpr = getFile.elements.filterByType[ScImportExpr]
    val importNamedMembers = importExpr.flatMap(_.explicitNamedMembers)
    importNamedMembers
      .map(member => member.visibleName -> member.reference.getText)
      .toSeq
  }
}
