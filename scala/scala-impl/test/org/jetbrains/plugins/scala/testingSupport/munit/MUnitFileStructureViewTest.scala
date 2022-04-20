package org.jetbrains.plugins.scala.testingSupport.munit

import org.jetbrains.plugins.scala.util.assertions.ExceptionAssertions

class MUnitFileStructureViewTest extends MUnitTestCase {

  private val ClassNameFunSuite = "MUnitFileStructureView_Test_FunSuite"
  private val FileNameFunSuite = ClassNameFunSuite + ".scala"

  addSourceFile(FileNameFunSuite,
    s"""import munit.FunSuite
       |
       |class $ClassNameFunSuite extends munit.FunSuite {
       |  test("test 1") {
       |  }
       |
       |  test("test 2") {
       |  }
       |
       |  test("test 3") {
       |  }
       |}
       |""".stripMargin
  )

  private val ClassNameScalaCheckSuite = "MUnitFileStructureView_Test_ScalaCheckSuite"
  private val FileNameScalaCheckSuite = ClassNameScalaCheckSuite + ".scala"

  addSourceFile(FileNameScalaCheckSuite,
    s"""import munit.ScalaCheckSuite
       |
       |import org.scalacheck.Prop.forAll
       |
       |class $ClassNameScalaCheckSuite extends ScalaCheckSuite {
       |  test("simple test") {
       |  }
       |
       |  property("property test") {
       |    forAll { (n1: Int, n2: Int) => n1 + n2 == n2 + n1 }
       |  }
       |}
       |""".stripMargin
  )

  def testFunSuite(): Unit =
    runFileStructureViewTest0(
      ClassNameFunSuite,
      AssertFileStructureTreePathsEqualsUnordered(Seq(
        FileStructurePath.p(s"""$ClassNameFunSuite.scala / $ClassNameFunSuite / [1] test("test 1")"""),
        FileStructurePath.p(s"""$ClassNameFunSuite.scala / $ClassNameFunSuite / [1] test("test 2")"""),
        FileStructurePath.p(s"""$ClassNameFunSuite.scala / $ClassNameFunSuite / [1] test("test 3")"""),
      ))
    )

  def testClassNameScalaCheckSuite(): Unit =
    runFileStructureViewTest0(
      ClassNameScalaCheckSuite,
      AssertFileStructureTreePathsEqualsUnordered(Seq(
        FileStructurePath.p(s"""$ClassNameScalaCheckSuite.scala / $ClassNameScalaCheckSuite / [1] test("simple test")"""),
        FileStructurePath.p(s"""$ClassNameScalaCheckSuite.scala / $ClassNameScalaCheckSuite / [1] property("property test")"""),
      ))
    )

  def testFunSuite_testPackage_EnsureAssertionFails(): Unit = ExceptionAssertions.assertException[java.lang.AssertionError] {
    runFileStructureViewTest0(
      ClassNameFunSuite,
      AssertFileStructureTreePathsEqualsUnordered(Seq(
        FileStructurePath.p(s"""$ClassNameFunSuite.scala / $ClassNameFunSuite / [1] test("test 1")""")
      ))
    )
  }
}
