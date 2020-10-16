package org.jetbrains.plugins.scala.testingSupport.munit

import org.jetbrains.plugins.scala.util.assertions.ExceptionAssertions

class MUnitFileStructureViewTest extends MUnitTestCase {

  val ClassName = "MUnitFileStructureViewTest"
  val FileName = ClassName + ".scala"

  addSourceFile(FileName,
    s"""import munit.FunSuite
       |
       |class $ClassName extends munit.FunSuite {
       |  test("test 1") {
       |  }
       |
       |  test("test 2") {
       |  }
       |
       |  test("test 3") {
       |  }
       |}
       |
       |""".stripMargin
  )

  def testFunSuite(): Unit =
    runFileStructureViewTest0(
      ClassName,
      AssertFileStructureTreePathsEqualsUnordered(Seq(
        FileStructurePath.p(s"""$ClassName.scala / $ClassName / [1] test("test 1")"""),
        FileStructurePath.p(s"""$ClassName.scala / $ClassName / [1] test("test 2")"""),
        FileStructurePath.p(s"""$ClassName.scala / $ClassName / [1] test("test 3")"""),
      ))
    )

  def testFunSuite_testPackage_EnsureAssertionFails(): Unit = ExceptionAssertions.assertException[java.lang.AssertionError] {
    runFileStructureViewTest0(
      ClassName,
      AssertFileStructureTreePathsEqualsUnordered(Seq(
        FileStructurePath.p(s"""$ClassName.scala / $ClassName / [1] test("test 1")""")
      ))
    )
  }
}
