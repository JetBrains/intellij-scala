package org.jetbrains.plugins.scala.lang.autoImport

import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.autoImport.ImportOrderings._
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.util.PsiSelectionUtil

class AutoImportSortingTest extends ScalaLightCodeInsightFixtureTestAdapter with PsiSelectionUtil {
  import org.junit.Assert._

  def check(@Language("Scala") code: String, refPath: NamedElementPath, localOrdering: PsiElement => Ordering[String], possibilities: Seq[String]): Unit = {
    val file = myFixture.configureByText(ScalaFileType.INSTANCE, code)

    val ref = selectElement[ScReference](file, refPath)
    // reverse them to make the input different from the result
    val imports = possibilities.reverse

    val ordering = localOrdering(ref)
    val result = imports.sorted(ordering)

    assertEquals(possibilities.mkString("\n"), result.mkString("\n"))
  }

  val alphabeticalSort: PsiElement => Ordering[String] = _ => orderingByPackageName
  val packageDistSort: PsiElement => Ordering[String] = e => orderingByDistanceToLocalImports(e) orElse specialPackageOrdering orElse orderingByPackageName

  def test_alphabetical_sorting(): Unit = check(
    """
      |new Ref
    """.stripMargin,
    path("Ref"),
    alphabeticalSort,
    Seq(
      "com.test.Ref",
      "com.test.inner.Ref",
      "com.xxx.Ref",
      "com.xxx.y.Ref"
    )
  )

  def test_package_dist_sorting(): Unit = check(
    """
      |package com.my.here
      |
      |import abc.test.last.SomethingElse
      |
      |object Obj {
      |  import zzz.zzz.SomethingElse2
      |
      |  new Ref
      |}
    """.stripMargin,
    path("Obj", "Ref"),
    packageDistSort,
    Seq(
      "com.my.here.Ref",          // dist 0 to com.my.here and is inner+curpack
      "com.my.here.inner.Ref",    // dist 1 to com.my.here and is inner+curpack
      "com.my.Ref",               // dist 1 to com.my.here and is curpack
      "abc.test.last.Ref",        // dist 0 to abc.test.last
      "zzz.zzz.Ref",              // dist 0 to zzz.zzz
      "abc.test.last.innerA.Ref", // dist 1 to abc.test.last and is inner
      "abc.test.last.innerB.Ref", // dist 1 to abc.test.last and is inner
      "zzz.zzz.a.Ref",            // dist 1 to zzz.zzz and is inner
      "abc.test.Ref",             // dist 1 to abc.test.last
      "zzz.zzz.a.b.Ref",          // dist 2 to zzz.zzz and is inner
      "abc.test.innerA.Ref",      // dist 2 to abc.test.last
      "abc.test.innerB.Ref",      // dist 2 to abc.test.last
      "zzz.zzz.a.b.c.Ref",        // dist 3 to zzz.zzz
      "abc.testa.Ref",            // unrelated
      "abc.testb.Ref",            // unrelated
      "abc.testc.Ref",            // unrelated
      "abc.unrelated.Ref"         // unrelated
    )
  )
}
