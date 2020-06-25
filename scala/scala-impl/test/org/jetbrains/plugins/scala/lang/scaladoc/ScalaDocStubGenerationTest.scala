package org.jetbrains.plugins.scala
package lang.scaladoc

class ScalaDocStubGenerationTest extends ScalaDocEnterActionTestBase {

  private val || = s"/**${|}"

  private def intended(code: String, spaces: Int = 2): String = {
    val indent = " " * spaces
    indent + code.replace("\n", s"\n$indent")
  }

  def doMethodTest(before: String, expectedAfter: String): Unit = {
    def wrap(s: String): String =
      s"""class A {
         |${intended(s)}
         |}""".stripMargin

    val beforeNew   = wrap(before)
    val expectedNew = wrap(expectedAfter)
    doTest(beforeNew, expectedNew)
  }

  def testSimpleMethodParamStub(): Unit =
    doMethodTest(
      s"""${||}
         |def f(i: Int, j: Int): Unit = ???""".stripMargin,
      s"""/**
         | * ${|}
         | * @param i
         | * @param j
         | */
         |def f(i: Int, j: Int): Unit = ???""".stripMargin
    )

  def testMethodSpecificStub(): Unit =
    doMethodTest(
      s"""${||}@throws(classOf[java.io.IOException])
         |def f(): Int = {1}""".stripMargin,
      s"""/**
         | * ${|}
         | * @throws java.io.IOException
         | * @return
         | */
         |@throws(classOf[java.io.IOException])
         |def f(): Int = {1}""".stripMargin
    )

  def testMixedMethodStub(): Unit =
    doMethodTest(
      s"""${||}def f[C](a: String, b: String): Int = 1""",
      s"""/**
         | * ${|}
         | * @param a
         | * @param b
         | * @tparam C
         | * @return
         | */
         |def f[C](a: String, b: String): Int = 1""".stripMargin,
    )

  def testClassStub(): Unit =
    doTest(
      s"${||}class A[E, T](i: Int, j: String) {}",
      s"""/**
         | * ${|}
         | * @param i
         | * @param j
         | * @tparam E
         | * @tparam T
         | */
         |class A[E, T](i: Int, j: String) {}""".stripMargin
    )

  def testClassEmptyStub(): Unit =
    doTest(
      s"${||}class A {}",
      s"""/**
         | * ${|}
         | */
         |class A {}""".stripMargin
    )

  def testTraitStub(): Unit =
    doTest(
      s"""${||}trait F[A, B, C] {}""",
      s"""/**
         | * ${|}
         | * @tparam A
         | * @tparam B
         | * @tparam C
         | */
         |trait F[A, B, C] {}""".stripMargin
    )

  def testTypeAliasStub(): Unit =
    doTest(
      s"""${||}type AA[A, B] = String""",
      s"""/**
         | * ${|}
         | * @tparam A
         | * @tparam B
         | */
         |type AA[A, B] = String""".stripMargin
    )

  def testInheritFromScala(): Unit =
    doTest(
      s"""trait Holder {
         |  /**
         |  *@tparam T qwerty
         | * @param a ytrewq
         |* @tparam E 12345
         |    * @param b 54321
         |   */
         |  class A[T, E](a: Int, b: String) {}
         |
         |  ${||}
         |  class B[T, E, U](a: Int, b: String, c: Any) extends A[T,E](a,b) {}
         |}""".stripMargin,
      s"""trait Holder {
         |  /**
         |  *@tparam T qwerty
         | * @param a ytrewq
         |* @tparam E 12345
         |    * @param b 54321
         |   */
         |  class A[T, E](a: Int, b: String) {}
         |
         |  /**
         |   * ${|}
         |   * @param a ytrewq
         |   * @param b 54321
         |   * @param c
         |   * @tparam T qwerty
         |   * @tparam E 12345
         |   * @tparam U
         |   */
         |  class B[T, E, U](a: Int, b: String, c: Any) extends A[T,E](a,b) {}
         |}""".stripMargin
    )

  def testOverrideScala(): Unit =
    doTest(
      s"""class A {
         | /**
         |  *   @tparam T lkjh
         | *@param i 777
         |*/
         |  def f[T](i: Int) {}
         |}
         |
         |class B extends A {
         |  ${||}
         |  override def f[T](i: Int) {}
         |}""".stripMargin,
      s"""class A {
         | /**
         |  *   @tparam T lkjh
         | *@param i 777
         |*/
         |  def f[T](i: Int) {}
         |}
         |
         |class B extends A {
         |  /**
         |   * ${|}
         |   * @param i 777
         |   * @tparam T lkjh
         |   */
         |  override def f[T](i: Int) {}
         |}""".stripMargin
    )

  def test_SCL9049(): Unit =
    doTest(
      s"""${||}def fooboobar(i: Int)(j: String) {}""",
      s"""/**
         | * ${|}
         | * @param i
         | * @param j
         | */
         |def fooboobar(i: Int)(j: String) {}""".stripMargin,
    )

  def test_SCL9049_1(): Unit =
    doTest(
      s"""${||}def fooboobar(i: Int) {}""",
      s"""/**
         | * ${|}
         | * @param i
         | */
         |def fooboobar(i: Int) {}""".stripMargin,
    )

  def testSCL16279(): Unit =
    doTest(
      s"""/*${|}*/
         |object Hello {}""".stripMargin,
      s"""/*
         |* ${|}*/
         |object Hello {}""".stripMargin,
    )

  def testSCL16279_1(): Unit =
    doTest(
      s"""object Hello {}
         |/*${|}*/""".stripMargin,
      s"""object Hello {}
         |/*
         |* ${|}*/""".stripMargin,
    )

  def testSCL16279_2(): Unit =
    doTest(
      s"""object Hello {}
         |/* ${|}*/""".stripMargin,
      s"""object Hello {}
         |/*
         |* ${|}*/""".stripMargin,
    )

  def testSCL16279_3(): Unit =
    doTest(
      s"""object Hello {}
         |/*${|}*/
         |class A""".stripMargin,
      s"""object Hello {}
         |/*
         |* ${|}*/
         |class A""".stripMargin,
    )

  def testSCL16279_ShouldNotCompleteDocIfCaretIsNotInsideDocContent(): Unit =
    doTest(
      s"""object Hello {}
         |/**${|}/""".stripMargin,
      s"""object Hello {}
         |/**
         |${|}/""".stripMargin,
    )

  def testCompleteInTheEndOfTheFile(): Unit = {
    // ignored due to buggy com.intellij.codeInsight.editorActions.EnterHandler.DoEnterAction.run, not important
    // because it's unlikely that someone will create doc stub in the end of file wihtout any method of class
    return

    val expected =
      s"""/**
         | * ${|}
         | */""".stripMargin
    doTest(s"/**${|}\n", expected)
    doTest(s"/** ${|}\n", expected)
    doTest(s"/**${|}", expected)
    doTest(s"/** ${|}", expected)
  }

  def testCompleteInTheEndOfTheFile_BlockComment(): Unit = {
    val expected =
      s"""/*
         |${|}
         | */""".stripMargin

    doTest(s"/*${|}\n", expected)
    doTest(s"/* ${|}\n", expected)
    doTest(s"/*${|}", expected)
    doTest(s"/* ${|}", expected)
  }
}