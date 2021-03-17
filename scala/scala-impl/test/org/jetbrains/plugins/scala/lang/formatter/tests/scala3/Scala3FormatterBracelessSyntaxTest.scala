package org.jetbrains.plugins.scala.lang.formatter.tests.scala3

class Scala3FormatterBracelessSyntaxTest extends Scala3FormatterBaseTest {

  // TODO: rename (replace 'L' with `l`)
  private def doTextTestForALlDefTypes(codeWithDefDef: String): Unit = {
    val codeWithValDef = codeWithDefDef.replaceFirst("def", "val")
    val codeWithVarDef = codeWithDefDef.replaceFirst("def", "var")

    doTextTest(codeWithDefDef)
    doTextTest(codeWithValDef)
    doTextTest(codeWithVarDef)
  }

  def testBodyWithSingleStatement(): Unit = doTextTestForALlDefTypes(
    """def foo(a: Int) =
      |  a match
      |    case 1 => 3
      |    case 2 => 4
      |""".stripMargin
  )

  def testBodyWithMultipleStatements_0(): Unit = doTextTestForALlDefTypes(
    """def foo(a: Int) =
      |  println("dummy")
      |  a match
      |    case 1 => 3
      |    case 2 => 4
      |""".stripMargin
  )

  def testBodyWithMultipleStatements_1(): Unit = doTextTestForALlDefTypes(
    """def foo(a: Int) =
      |  a match
      |    case 1 => 3
      |    case 2 => 4
      |  println("dummy")
      |""".stripMargin
  )

  def testBodyWithMultipleStatements_2(): Unit = doTextTestForALlDefTypes(
    """def foo(a: Int) =
      |  println("dummy")
      |  a match
      |    case 1 => 3
      |    case 2 => 4
      |  println("dummy")
      |""".stripMargin
  )

  def testBodyWithSingleInnerDefinition(): Unit = doTextTestForALlDefTypes(
    """def foo(a: Int) =
      |  val b = a match
      |    case 1 => 3
      |    case 2 => 4
      |""".stripMargin
  )

  def testBodyWithInnerDefinitionAndOtherStatements_0(): Unit = doTextTestForALlDefTypes(
    """def foo(a: Int) =
      |  println("dummy")
      |  val b = a match
      |    case 1 => 3
      |    case 2 => 4
      |""".stripMargin
  )

  def testBodyWithInnerDefinitionAndOtherStatements_1(): Unit = doTextTestForALlDefTypes(
    """def foo(a: Int) =
      |  val b = a match
      |    case 1 => 3
      |    case 2 => 4
      |  println("dummy")
      |""".stripMargin
  )

  def testBodyWithInnerDefinitionAndOtherStatements_2(): Unit = doTextTestForALlDefTypes(
    """def foo(a: Int) =
      |  println("dummy")
      |  val b = a match
      |    case 1 => 3
      |    case 2 => 4
      |  println("dummy")
      |""".stripMargin
  )

  def testConstructorBody_WithBraces(): Unit = doTextTest(
    """class A {
      |  def this(x: Long) = {
      |    this()
      |  }
      |
      |  def this(x: Short) = {
      |    this()
      |    println(1)
      |  }
      |
      |  def this(x: Long, y: Long) = {
      |    this()
      |  }
      |
      |  def this(x: Short, y: Short) = {
      |    this()
      |    println(1)
      |  }
      |}
      |""".stripMargin
  )

  def testConstructorBody_WithoutBraces(): Unit = doTextTest(
    """class A {
      |  def this(x: Int) =
      |    this()
      |
      |  def this(x: String) =
      |    this()
      |    println(2)
      |
      |  def this(x: Int, y: Int) =
      |    this()
      |  end this
      |
      |  def this(x: String, y: String) =
      |    this()
      |    println(2)
      |  end this
      |}
      |""".stripMargin
  )

  def testFor_GeneratorWithIndentedBlock(): Unit = doForYieldDoTest(
    """for {
      |  x <-
      |    var a = 1
      |    val b = 3
      |    a to b
      |}
      |yield x
      |""".stripMargin
  )

  def testFor_GeneratorWithBlock(): Unit = doForYieldDoTest(
    """for {
      |  x <- {
      |    var a = 1
      |    val b = 3
      |    a to b
      |  }
      |}
      |yield x
      |""".stripMargin
  )

  def testFor_GeneratorWithBlock_WithSingleExpressionOnNewLine(): Unit = doForYieldDoTest(
    """for {
      |  x <-
      |    1 to 2
      |}
      |yield x
      |""".stripMargin
  )

  def testFor_PatternEnumerator_WithIndentedBlock(): Unit = doForYieldDoTest(
    """for {
      |  x <- 1 to 2
      |  y =
      |    var a = 1
      |    val b = 3
      |    a to b
      |}
      |yield x
      |""".stripMargin
  )

  def testFor_PatternEnumerator_WithBlock(): Unit = doForYieldDoTest(
    """for {
      |  x <- 1 to 2
      |  y = {
      |    var a = 1
      |    val b = 3
      |    a to b
      |  }
      |}
      |yield x
      |""".stripMargin
  )

  def testFor_PatternEnumerator_WithSingleExpressionOnNewLine(): Unit = doForYieldDoTest(
    """for {
      |  x <- 1 to 2
      |  y =
      |    42
      |}
      |yield x
      |""".stripMargin
  )
}
