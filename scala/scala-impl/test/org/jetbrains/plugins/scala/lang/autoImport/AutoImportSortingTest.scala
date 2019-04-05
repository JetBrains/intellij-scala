package org.jetbrains.plugins.scala.lang.autoImport

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.Sorter
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.util.PsiSelectionUtil

class AutoImportSortingTest extends ScalaLightCodeInsightFixtureTestAdapter with PsiSelectionUtil {
  import org.junit.Assert._

  class ClassTypeToImportMock(override val qualifiedName: String) extends ScalaImportTypeFix.ClassTypeToImport(null) {
    override def name: String = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)

    override def isAnnotationType: Boolean = ???

    override def isValid: Boolean = ???

    override def toString: String = s"Mock[$qualifiedName]"
  }

  def check(@Language("Scala") code: String, refPath: NamedElementPath, sorter: Sorter, possibilities: Seq[String]): Unit = {
    val file = myFixture.configureByText(ScalaFileType.INSTANCE, code)

    val ref = selectElement[ScReference](file, refPath)
    // reverse them to make the input different from the result
    val imports = possibilities.reverse.map(new ClassTypeToImportMock(_))

    val result =
      sorter(imports, ref, ref.getProject)
        .map(_.qualifiedName)

    assertEquals(possibilities.mkString("\n"), result.mkString("\n"))
  }

  val alphabeticalSort: Sorter = ScalaImportTypeFix.sortImportsByName
  val packageDistSort: Sorter = ScalaImportTypeFix.sortImportsByPackageDistanceWithFallbackSorter

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
      |  new Ref
      |}
    """.stripMargin,
    path("Obj", "Ref"),
    packageDistSort,
    Seq(
      "com.my.here.Ref",
      "com.my.here.inner.Ref",
      "com.my.Ref",
      "abc.test.last.Ref",
      "abc.test.last.innerA.Ref",
      "abc.test.last.innerB.Ref",
      "abc.test.Ref",
      "abc.test.innerA.Ref",
      "abc.test.innerB.Ref",
      "abc.testa.Ref",
      "abc.testb.Ref",
      "abc.testc.Ref"
    )
  )
}
