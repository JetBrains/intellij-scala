package org.jetbrains.plugins.scala
package codeInsight.intentions.imports

class ImportAllMembersIntentionTest extends ImportAllMembersIntentionBaseTest {

  def testPackageNameConflictWithMethod(): Unit ={
    val text =
      """package foo {
        |
        |  object A {
        |    def foo(): Unit = {
        |
        |    }
        |  }
        |
        |}
        |
        |object B {
        |  foo.<caret>A.foo()
        |}""".stripMargin

    val result =
      """import foo.A._
        |package foo {
        |
        |  object A {
        |    def foo(): Unit = {
        |
        |    }
        |  }
        |
        |}
        |
        |object B {
        |  foo()
        |}""".stripMargin
    doTest(text, result)
  }
}
