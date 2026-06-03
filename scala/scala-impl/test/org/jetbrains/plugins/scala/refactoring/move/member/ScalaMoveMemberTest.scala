package org.jetbrains.plugins.scala.refactoring.move.member

import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import org.junit.Assert.fail

class ScalaMoveMemberTest extends BaseScalaMoveMemberTest {

  def testVal(): Unit = {
    doTest("A", "B", "x",
      """
        |object A {
        |  val x = 1
        |}
        |
        |object B
      """.stripMargin,
      """
        |object A {
        |}
        |
        |object B {
        |  val x = 1
        |}
      """.stripMargin
    )
  }

  def testVar(): Unit = {
    doTest("A", "B", "x",
      """
        |object A {
        |  var x = 1
        |}
        |
        |object B
      """.stripMargin,
      """
        |object A {
        |}
        |
        |object B {
        |  var x = 1
        |}
      """.stripMargin
    )

  }

  def testDef(): Unit = {
    doTest("A", "B", "x",
      """
        |object A {
        |  def x = 1
        |}
        |
        |object B
      """.stripMargin,
      """
        |object A {
        |}
        |
        |object B {
        |  def x = 1
        |}
      """.stripMargin
    )

  }

  def testUsagesInSource(): Unit = {
    doTest("a.A", "b.B", "foo",
    """
      |package a {
      |
      |  class Foo
      |  object Foo {
      |    def create = new Foo
      |  }
      |
      |  object A {
      |    def foo: Foo = Foo.create
      |    def nonQual = foo
      |    def qual = A.foo
      |  }
      |}
      |
      |package b {
      |  object B
      |}
    """.stripMargin,
    """
      |package a {
      |
      |  import b.B
      |
      |  class Foo
      |  object Foo {
      |    def create = new Foo
      |  }
      |
      |  object A {
      |    def nonQual = B.foo
      |    def qual = B.foo
      |  }
      |}
      |
      |package b {
      |
      |  import a.Foo
      |
      |  object B {
      |    def foo: Foo = Foo.create
      |  }
      |}""".stripMargin)
  }

  def testUsagesInTarget(): Unit = {
    doTest("a.A", "b.B", "foo",
      """
        |package a {
        |
        |  class Foo
        |  object Foo {
        |    def create = new Foo
        |  }
        |
        |  object A {
        |    def foo: Foo = Foo.create
        |  }
        |}
        |
        |package b {
        |
        |  object B {
        |    def aafoo = a.A.foo
        |
        |    import a.A
        |    def afoo = A.foo
        |
        |    import a.A.foo
        |    def nonQual = foo
        |  }
        |
        |  object B1 {
        |    def aafoo = a.A.foo
        |
        |    import a.A
        |    def afoo = A.foo
        |
        |    import a.A.foo
        |    def nonQual = foo
        |  }
        |}
      """.stripMargin,
      """
        |package a {
        |
        |  class Foo
        |  object Foo {
        |    def create = new Foo
        |  }
        |
        |  object A {
        |  }
        |}
        |
        |package b {
        |
        |  import a.Foo
        |
        |  object B {
        |    def aafoo = foo
        |
        |    import a.A
        |    def afoo = foo
        |
        |    def nonQual = foo
        |
        |    def foo: Foo = Foo.create
        |  }
        |
        |  object B1 {
        |    def aafoo = B.foo
        |
        |    import a.A
        |    def afoo = B.foo
        |
        |    import B.foo
        |    def nonQual = foo
        |  }
        |}
      """.stripMargin
    )
  }

  def testTypeAlias(): Unit = {
    doTest("a.A", "b.B", "F",
      """
        |package a {
        |
        |  class Foo
        |
        |  object A {
        |    type F = Foo
        |    val f: F = ???
        |  }
        |}
        |
        |package b {
        |
        |  object B {
        |    val f: a.A.F = ???
        |  }
        |}
      """.stripMargin,
      """
        |package a {
        |
        |  import b.B
        |
        |  class Foo
        |
        |  object A {
        |    val f: B.F = ???
        |  }
        |}
        |
        |package b {
        |
        |  import a.Foo
        |
        |  object B {
        |    val f: F = ???
        |    type F = Foo
        |  }
        |}
      """.stripMargin
    )
  }

  def testMoveNonSimpleVal(): Unit = {
    doTest("a.A", "b.B", "x",
      """
        |package a {
        |  object A {
        |    val x, y = 1
        |  }
        |}
        |
        |package b {
        |  object B {
        |    def z = 2
        |  }
        |}
      """.stripMargin,
      """
        |package a {
        |  object A {
        |  }
        |}
        |
        |package b {
        |  object B {
        |    def z = 2
        |
        |    val x, y = 1
        |  }
        |}
      """.stripMargin)
  }

  def testConflict(): Unit = {
    try {
      doTest("A", "B", "x",
        """
          |object A {
          |  def x = 1
          |}
          |
          |object B {
          |  def x = 1
          |}
        """.stripMargin,
        null
      )
      fail("expected 'ConflictsInTestsException'")
    } catch {
      case _:ConflictsInTestsException =>
      case _: Throwable => fail("expected 'ConflictsInTestsException'")
    }
  }

  def testConflict2(): Unit = {
    try {
      doTest("A", "B", "x",
        """
          |object A {
          |  val x, y = 1
          |}
          |
          |object B {
          |  def x = 1
          |}
        """.stripMargin,
        null
      )
      fail("expected 'ConflictsInTestsException'")
    } catch {
      case _:ConflictsInTestsException =>
      case _: Throwable => fail("expected 'ConflictsInTestsException'")
    }
  }

}
