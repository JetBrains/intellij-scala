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

  // Taken from:
  // http://dotty.epfl.ch/docs/reference/other-new-features/indentation.html#the-end-marker
  def testEndMarker_AllInOne(): Unit = doTextTest(
    """package p1.p2:
      |
      |  abstract class C():
      |
      |    def this(x: Int) =
      |      this()
      |      if x > 0 then
      |        val a :: b =
      |          x :: Nil
      |        end val
      |        var y =
      |          x
      |        end y
      |        while y > 0 do
      |          println(y)
      |          y -= 1
      |        end while
      |        try
      |          x match
      |            case 0 => println("0")
      |            case _ =>
      |          end match
      |        finally
      |          println("done")
      |        end try
      |      end if
      |    end this
      |
      |    def f: String
      |  end C
      |
      |  object C:
      |    given C =
      |      new C :
      |        def f = "!"
      |        end f
      |      end new
      |    end given
      |  end C
      |
      |  extension (x: C)
      |    def ff: String = x.f ++ x.f
      |  end extension
      |
      |end p2
      |""".stripMargin,
    actionRepeats = 3
  )

  private val CommentPlaceholder = "<CommentPlaceholder>"
  private def doTestWithAllComments(before: String): Unit = {
    assert(before.contains(CommentPlaceholder))

    doTextTest(before.replace(CommentPlaceholder, ""))
    doTextTest(before.replace(CommentPlaceholder, "// line comment"))
    doTextTest(before.replace(CommentPlaceholder, "/* block comment */"))
    doTextTest(before.replace(CommentPlaceholder,
      """
        |/**
        | * doc comment
        | */""".stripMargin)
    )
  }

  def testAssign_ToId_WithIndentedBlock(): Unit = doTestWithAllComments(
    s"""class A {
       |  var x: Int = 0
       |}
       |
       |val a = new A
       |$CommentPlaceholder
       |a.x =
       |  var x = 1
       |  var y = 2
       |  x + y
       |""".stripMargin
  )

  def testAssign_ToId_WithBlock(): Unit = doTestWithAllComments(
    s"""class A {
       |  var x: Int = 0
       |}
       |
       |val a = new A
       |$CommentPlaceholder
       |a.x = {
       |  var x = 1
       |  var y = 2
       |  x + y
       |}
       |""".stripMargin
  )

  def testAssign_ToId_WithSingleExpression(): Unit = doTestWithAllComments(
    s"""class A {
       |  var x: Int = 0
       |}
       |
       |val a = new A
       |$CommentPlaceholder
       |a.x =
       |  1 + 2
       |""".stripMargin
  )

  def testAssign_ToMap_ViaUpdateMethod_WithIndentedBlock(): Unit = doTestWithAllComments(
    s"""val map = scala.collection.mutable.Map.empty[Int, Int]
       |$CommentPlaceholder
       |map(42) =
       |  var x = 1
       |  var y = 2
       |  x + y
       |""".stripMargin
  )

  def testAssign_ToMap_ViaUpdateMethod_WithBlock(): Unit = doTestWithAllComments(
    s"""val map = scala.collection.mutable.Map.empty[Int, Int]
       |$CommentPlaceholder
       |map(42) = {
       |  var x = 1
       |  var y = 2
       |  x + y
       |}
       |""".stripMargin
  )

  def testAssign_ToMap_ViaUpdateMethod_WithSingleExpression(): Unit = doTestWithAllComments(
    s"""val map = scala.collection.mutable.Map.empty[Int, Int]
       |$CommentPlaceholder
       |map(42) =
       |  1 + 2
       |""".stripMargin
  )

  def testThrow(): Unit = doTextTest(
    """throw
      |  1
      |
      |throw
      |  1
      |  2
      |
      |throw
      |  1
      |  2
      |  3
      |
      |throw
      |  var x = 1
      |  var x = 2
      |  3
      |
      |throw
      |  class A
      |  var x = 2
      |  3
      |
      |throw
      |  var x = 1
      |
      |throw
      |  class A
      |""".stripMargin
  )

  def testNotIndentedBody_FunDef(): Unit = doTextTest(
    """def foo =
      |println("foo 1")
      |println("foo 2")
      |
      |class A {
      |  def foo =
      |  println("foo 1")
      |  println("foo 2")
      |}""".stripMargin,
    """def foo =
      |  println("foo 1")
      |println("foo 2")
      |
      |class A {
      |  def foo =
      |    println("foo 1")
      |
      |  println("foo 2")
      |}""".stripMargin
  )

  def testNotIndentedBody_IfThenElse(): Unit = doTextTest(
    """class A {
      |  if true then
      |  println(1)
      |  println(11)
      |
      |  if true then
      |  println(1)
      |  else
      |  println(2)
      |  println(22)
      |}""".stripMargin,
    """class A {
      |  if true then
      |    println(1)
      |  println(11)
      |
      |  if true then
      |    println(1)
      |  else
      |    println(2)
      |  println(22)
      |}""".stripMargin
  )

  def testUnindentedBody_FunDef(): Unit = doTextTest(
    """class A {
      |  def foo =
      | println("foo 1")
      |  println("foo 2")
      |}""".stripMargin,
    """class A {
      |  def foo =
      |    println("foo 1")
      |
      |  println("foo 2")
      |}""".stripMargin
  )

  def testUnindentedBody_IfThenElse(): Unit = doTextTest(
    """class A {
      |  if true then
      | println(1)
      |  println(11)
      |
      |  if true then
      |  println(1)
      |  else
      |println(2)
      |  println(22)
      |}""".stripMargin,
    """class A {
      |  if true then
      |    println(1)
      |  println(11)
      |
      |  if true then
      |    println(1)
      |  else
      |    println(2)
      |  println(22)
      |}""".stripMargin
  )

  def testAfterReturnKeyword_1(): Unit = doTextTest(
    """def foo: String =
      |    return
      |        val x = 1
      |           val y = 2
      |               s"result: $x $y
      |""".stripMargin,
    """def foo: String =
      |  return
      |    val x = 1
      |    val y = 2
      |    s"result: $x $y
      |""".stripMargin,
    repeats = 3
  )

  def testAfterReturnKeyword_SingleExpression(): Unit = doTextTest(
    """def foo: String =
      |    return
      |        "result"
      |""".stripMargin,
    """def foo: String =
      |  return
      |    "result"
      |""".stripMargin,
    repeats = 3
  )
}
