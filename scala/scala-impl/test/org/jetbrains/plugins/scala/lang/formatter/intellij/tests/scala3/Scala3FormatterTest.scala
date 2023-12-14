package org.jetbrains.plugins.scala.lang.formatter.intellij.tests.scala3

import com.intellij.psi.codeStyle.CommonCodeStyleSettings

class Scala3FormatterTest extends Scala3FormatterBaseTest {

  def testColon_AfterTypeDefinition(): Unit = doTextTest(
    """trait Trait:
      |  def test = ()
      |
      |class Class :
      |  def test = ()
      |
      |object Object  :
      |  def test = ()
      |
      |enum Enum   :
      |  case A
      |  case B""".stripMargin,
    """trait Trait:
      |  def test = ()
      |
      |class Class:
      |  def test = ()
      |
      |object Object:
      |  def test = ()
      |
      |enum Enum:
      |  case A
      |  case B""".stripMargin
  )

  def testColon_AfterTypeDefinition_WithParam(): Unit = doTextTest(
    """trait Trait(param: String):
      |  def test = ()
      |
      |class Class(param: String) :
      |  def test = ()
      |
      |enum Enum(param: String)   :
      |  case A
      |  case B""".stripMargin,
    """trait Trait(param: String):
      |  def test = ()
      |
      |class Class(param: String):
      |  def test = ()
      |
      |enum Enum(param: String):
      |  case A
      |  case B""".stripMargin
  )

  def testColon_AfterTypeDefinition_WithExtends(): Unit = doTextTest(
    """trait Test extends Object:
      |  def test = ()
      |
      |class Test extends Object :
      |  def test = ()
      |
      |object Test extends Object with T :
      |  def test = ()
      |
      |enum Enum extends Object   :
      |  case A
      |  case B
      |""".stripMargin,
    """trait Test extends Object:
      |  def test = ()
      |
      |class Test extends Object:
      |  def test = ()
      |
      |object Test extends Object with T:
      |  def test = ()
      |
      |enum Enum extends Object:
      |  case A
      |  case B
      |""".stripMargin
  )

  def testColon_AfterTypeDefinition_WithParam_WithExtends(): Unit = doTextTest(
    """trait Test(p: Int)extends Object :
      |  def test = ()
      |
      |class Test()extends Object  :
      |  def test = ()
      |
      |enum Enum()extends Object   :
      |  case A
      |  case B
      |""".stripMargin,
    """trait Test(p: Int) extends Object:
      |  def test = ()
      |
      |class Test() extends Object:
      |  def test = ()
      |
      |enum Enum() extends Object:
      |  case A
      |  case B
      |""".stripMargin
  )

  def testClassEnd(): Unit = doTextTest(
    """
      |class Test:
      |  def test = ()
      |end Test
      |""".stripMargin
  )

  //SCL-18678
  def testClassEnd_1(): Unit = doTextTest(
    """def foo(n: Int): Int =
      |  def f(x: Int) = x + 1
      |
      |  f(n)
      |end foo
      |""".stripMargin
  )

  def testEnum(): Unit = doTextTest(
    """enum MyEnum {
      |  case A
      |  case B
      |}
      |""".stripMargin
  )

  def testEnum_WithModifiersAndAnnotations(): Unit = doTextTest(
    """enum MyEnum {
      |  protected case A
      |  final case B // NOTE: only access modifiers are supported but we shouldn't fail anyway
      |  @deprecated case B
      |  @deprecated
      |  case C
      |  @deprecated
      |  protected case C
      |}
      |""".stripMargin
  )

  def testEnum_WithModifiersAndAnnotations_InMembers(): Unit = doTextTest(
    """enum MyEnum {
      |  final val a = 0
      |  lazy val b = 0
      |  protected val c = 0
      |  private final val d = 0
      |
      |  @deprecated
      |  private final val e = 0
      |
      |  @deprecated
      |  private def f1 = 0
      |
      |  protected final def f2 = 0
      |
      |  final def f3 = 0
      |
      |  final type X = String
      |
      |  protected object Inner
      |
      |  case A
      |  case B
      |}
      |""".stripMargin
  )

  def testTypeLambda_InAlias(): Unit = {
    val after = "type Tuple = [X] =>> (X, X)"
    val before1 = "type Tuple=[X]=>>(X,X)"
    val before2 = "type   Tuple  =  [  X  ]  =>>  (  X  ,  X  )"

    doTextTest(after, after)
    doTextTest(before1, after)
    doTextTest(before2, after)
  }

  def testTypeLambda_WithBounds_InAlias(): Unit = {
    val after = "type TL1 = [X >: L1 <: U1] =>> R1"
    val before1 = "type TL1=[X>:L1<:U1]=>>R1"
    val before2 = "type  TL1  =  [  X  >:  L1  <:  U1  ]  =>>  R1"

    doTextTest(after, after)
    doTextTest(before1, after)
    doTextTest(before2, after)
  }

  def testTypeLambda_AsTypeAliasTypeParameterBound(): Unit = {
    val after = "type T >: ([X] =>> L) <: ([X] =>> U)"
    val before1 = "type T>:([X]=>>L)<:([X]=>>U)"
    val before2 = "type T  >:  (  [  X  ]  =>>  L  )  <:  (  [  X  ]  =>>  U  )"
    doTextTest(after, after)
    doTextTest(before1, after)
    doTextTest(before2, after)
  }

  def testTypeLambda_AsTypeParameterBound(): Unit = {
    val after = "def foo[F >: Nothing <: [X] =>> Coll[X]]: Unit = ???"
    val before1 = "def foo[F>:Nothing<:[X]=>>Coll[X]]: Unit = ???"
    val before2 = "def foo[  F  >:  Nothing  <:  [  X  ]  =>>  Coll[  X  ]  ]: Unit = ???"
    doTextTest(after, after)
    doTextTest(before1, after)
    doTextTest(before2, after)
  }

  def testGivenAlias_1(): Unit = doTextTest(
    """given IntWrapperToDoubleWrapper: Conversion[IntWrapper, DoubleWrapper] = new Conversion[IntWrapper, DoubleWrapper] {
      |  override def apply(i: IntWrapper): DoubleWrapper = new DoubleWrapper(i.a.toDouble)
      |}
      |""".stripMargin
  )

  def testGivenAlias_2(): Unit = doTextTest(
    """given stringParser: StringParser[String] = baseParser(Success(_))
      |
      |given intParser: StringParser[Int] = baseParser(s ⇒ Try(s.toInt))
      |""".stripMargin
  )

  def testGivenAlias_3(): Unit = doTextTest(
    """given optionParser[A](using parser: => StringParser[A]): StringParser[Option[A]] = new StringParser[Option[A]] {
      |  override def parse(s: String): Try[Option[A]] = s match {
      |    case "" ⇒ Success(None) // implicit parser not used.
      |    case str ⇒ parser.parse(str).map(x ⇒ Some(x)) // implicit parser is evaluated at here
      |  }
      |}
      |""".stripMargin
  )

  def testGivenDefinition_WithoutEqualsSign(): Unit = doTextTest(
    """given Id: Object with {
      |  def msg: String = ""
      |}
      |""".stripMargin
  )

  def testGivenDefinition_WithIndentationBasedTemplateBody(): Unit = doTextTest(
    """given intOrd: Ordering[Int] with
      |  def compare(x: Int, y: Int): Int = 42
      |
      |trait MyTrait
      |
      |given intOrd: Ordering[Int] with MyTrait with
      |  def compare(x: Int, y: Int): Int = 42
      |""".stripMargin
  )

  def testGivenDefinition_WithUsingParameterClause(): Unit = doTextTest(
    """given ByteOrdering(using Int, Short): Ordering[Byte] with {}""".stripMargin
  )

  def testGivenDefinition_WithUsingParameterClauseAndTypeParameters(): Unit = doTextTest(
    """given ShortOrdering[T, X](using T, X): Ordering[Short] with {}""".stripMargin
  )

  def testGivenDefinition_Anonymous(): Unit = doTextTest(
    """given Ordering[Int] with {}""".stripMargin
  )

  def testGivenDefinition_Anonymous_WithUsingParameterClause(): Unit = doTextTest(
    """given (using Int, Short): Ordering[Byte] with {}""".stripMargin
  )

  def testGivenDefinition_Anonymous_WithUsingParameterClauseAndTypeParameters(): Unit = doTextTest(
    """given [T, X](using T, X): Ordering[Short] with {}""".stripMargin
  )

  def testGivens_Incomplete(): Unit = {
    getCommonSettings.BLANK_LINES_AROUND_METHOD = 0
    getCommonSettings.BLANK_LINES_AROUND_CLASS = 0

    doTextTest(
      """class C {
        |  class MyClass1
        |  class MyClass2(using String)
        |  class MyClass3(using String)
        |
        |  given MyClass1()
        |  given MyClass2(using)
        |  given MyClass3(using "42")
        |
        |  given foo1(using String): String
        |  given foo2(using String): String =
        |  given foo3(using String): String = ???
        |}
        |
        |class D {
        |  class MyClass1[T]
        |  class MyClass2[T](using String)
        |  class MyClass3[T](using String)
        |
        |  given MyClass1[String]()
        |  given MyClass2[String](using)
        |  given MyClass3[String](using "42")
        |
        |  given foo1[T](using String): String
        |  given foo2[T](using String): String =
        |  given foo3[T](using String): String = ???
        |}
        |""".stripMargin
    )
  }

  def testGivens_Incomplete_Anonymous(): Unit = {
    getCommonSettings.BLANK_LINES_AROUND_METHOD = 0
    getCommonSettings.BLANK_LINES_AROUND_CLASS = 0

    doTextTest(
      """class A {
        |  given ()
        |  given (using)
        |  given (using String)
        |  given (using String): String
        |  given (using String): String =
        |  given (using String): String = ???
        |}
        |
        |class B {
        |  given [T]()
        |  given [T](using)
        |  given [T](using String)
        |  given [T](using String): String
        |  given [T](using String): String =
        |  given [T](using String): String = ???
        |}""".stripMargin
    )
  }

  def testPackagingWithColon(): Unit = {
    doTextTest(
      """package p1:
        |  def a = 1
        |
        |  package p2:
        |    def b = 2
        |""".stripMargin)
  }

  def testPackagingWithBraces(): Unit = {
    doTextTest(
      """package p1 {
        |  def a = 1
        |
        |  package p2 {
        |    def b = 2
        |  }
        |
        |}
        |""".stripMargin)
  }

  def testTypeMatch_0(): Unit =
    doTextTest(
      """type Widen[Tup <: Tuple] <: Tuple = Tup match {
        |  case EmptyTuple => EmptyTuple
        |  case h *: t => h *: t
        |}
        |""".stripMargin
    )

  def testTypeMatch_1(): Unit =
    doTextTest(
      """type Widen[Tup <: Tuple] <: Tuple =
        |  Tup match {
        |    case EmptyTuple => EmptyTuple
        |    case h *: t => h *: t
        |  }
        |""".stripMargin
    )

  def testTypeMatch_Braceless_0(): Unit =
    doTextTest(
      """type Widen[Tup <: Tuple] <: Tuple = Tup match
        |  case EmptyTuple => EmptyTuple
        |  case h *: t => h *: t
        |""".stripMargin
    )

  def testTypeMatch_Braceless_1(): Unit =
    doTextTest(
      """type Widen[Tup <: Tuple] <: Tuple =
        |  Tup match
        |    case EmptyTuple => EmptyTuple
        |    case h *: t => h *: t
        |""".stripMargin
    )

  def testContextFunctionExpression(): Unit = doTextTest(
    """val x = (a: Int) ?=> 3
      |
      |val x = (a: Int) ?=>
      |  3
      |
      |val x = (a: Int) ?=>
      |  println(1)
      |  println(2)
      |  3
      |
      |val x = (a: Int) ?=> {
      |  println(1)
      |  println(2)
      |  3
      |}
      |""".stripMargin
  )

  def testContextFunctionType(): Unit = doTextTest(
    """import scala.concurrent.{ExecutionContext, Future}
      |
      |type Contextual1[T] = ExecutionContext ?=> T
      |
      |type Contextual2[T] = ExecutionContext ?=>
      |  T
      |
      |type Contextual3[T] =
      |  ExecutionContext ?=> T
      |""".stripMargin
  )

  def testExport(): Unit = doTextTest(
    """
      |export a.*
      |
      |  export    a.b.c
      |
      |   export    a.b.{given}
      |
      |class A {
      |export a.*
      |
      |        export    a.b.c
      |
      |   export    a.b.{given}
      |}
      |""".stripMargin,
    """
      |export a.*
      |
      |export a.b.c
      |
      |export a.b.{given}
      |
      |class A {
      |  export a.*
      |
      |  export a.b.c
      |
      |  export a.b.{given}
      |}
      |""".stripMargin
  )

  //SCL-19811
  def testPatternMatchInForGuard(): Unit = {
    doTextTest(
      """for {
        |  n <- 1 to 8 if n match {
        |    case x if x > 5 => true
        |    case _ => false
        |  }
        |} yield n
        |""".stripMargin
    )
  }

  //see also ScalaBugsTest.testSCL2066FromDiscussion
  def testDoNotPlaceBraceOnNextLineWhenPassingBlockArgumentViaInfixNotation(): Unit = {
    getCommonSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTextTest(
      """val n = Seq(1, 2, 3)
        |n.foreach { //this must NOT go to the next line
        |  x => { //this must go to the next line
        |    println(x)
        |  }
        |}
        |""".stripMargin,
      """val n = Seq(1, 2, 3)
        |n.foreach { //this must NOT go to the next line
        |  x =>
        |  { //this must go to the next line
        |    println(x)
        |  }
        |}
        |""".stripMargin
    )
  }

  def testExtensions(): Unit = {
    doTextTest(
      """extension (p: String) def foo: String = ???
        |
        |extension (p: String)
        |  def foo: String
        |
        |extension [T, E](y: D)(using String, Int)(using Long)
        |  def gg: String = x.f ++ x.f
        |
        |extension [T, E](y: D)(using String, Int)(using Long) {
        |  def gg: String = x.f ++ x.f
        |}
        |""".stripMargin
    )
  }

  def testExtensionsWithComments(): Unit = {
    doTextTest(
      """extension (self: String)
        |
        |  /** description 1 */
        |  def pos: Int
        |  /** description 2 */
        |  def range: (Int, Int)
        |
        |extension (self: String)
        |  /* description 1 */
        |  def pos2: Int
        |  /* description 2 */
        |  def range2: (Int, Int)
        |
        |extension (self: String)
        |  // description 1
        |  def pos3: Int
        |  // description 2
        |  def range3: (Int, Int)
        |""".stripMargin
    )
  }
}
