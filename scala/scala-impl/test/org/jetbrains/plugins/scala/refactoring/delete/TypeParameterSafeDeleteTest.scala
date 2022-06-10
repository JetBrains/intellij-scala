package org.jetbrains.plugins.scala.refactoring.delete

class TypeParameterSafeDeleteTest extends ScalaSafeDeleteTestBase {

  def test_unreferenced(): Unit = doSafeDeleteTest(
    s"""
       |def test[Ty${|}pe] = {}
       |""".stripMargin,
    """
      |def test = {}
      |""".stripMargin,
  )

  def test_multi_param_delete_first(): Unit = doSafeDeleteTest(
    s"""
       |def test[Fi${|}rst, Middle, Last]() = {}
       |
       |test[Int, String, Boolean]()
       |""".stripMargin,
    """
      |def test[Middle, Last]() = {}
      |
      |test[String, Boolean]()
      |""".stripMargin,
  )

  def test_multi_param_delete_middle(): Unit = doSafeDeleteTest(
    s"""
       |def test[First, Mid${|}dle, Last]() = {}
       |
       |test[Int, String, Boolean]()
       |""".stripMargin,
    """
      |def test[First, Last]() = {}
      |
      |test[Int, Boolean]()
      |""".stripMargin,
  )

  def test_multi_param_delete_last(): Unit = doSafeDeleteTest(
    s"""
       |def test[First, Middle, La${|}st]() = {}
       |
       |test[Int, String, Boolean]()
       |""".stripMargin,
    """
      |def test[First, Middle]() = {}
      |
      |test[Int, String]()
      |""".stripMargin,
  )

  def test_delete_trailing_comma(): Unit = doSafeDeleteTest(
    s"""
       |def test[
       |  First,
       |  La${|}st ,
       |]() = {}
       |
       |test[
       |  Int,
       |  String,
       |]()
       |""".stripMargin,
    """
      |def test[
      |  First,
      |]() = {}
      |
      |test[
      |  Int,
      |]()
      |""".stripMargin,
  )

  def test_multi_trait_param_delete_first(): Unit = doSafeDeleteTest(
    s"""
       |trait TestTrait[Fi${|}rst, Middle, Last]
       |
       |def test = new TestTrait[Int, String, Boolean] {}
       |""".stripMargin,
    """
      |trait TestTrait[Middle, Last]
      |
      |def test = new TestTrait[String, Boolean] {}
      |""".stripMargin,
  )

  def test_multi_class_param_delete_middle(): Unit = doSafeDeleteTest(
    s"""
       |class TestClass[First, Mid${|}dle, Last]
       |
       |def test = new TestClass[Int, String, Boolean]
       |""".stripMargin,
    """
      |class TestClass[First, Last]
      |
      |def test = new TestClass[Int, Boolean]
      |""".stripMargin,
  )

  def test_multi_case_class_param_delete_last(): Unit = doSafeDeleteTest(
    s"""package tests.safe.delete {
       |  case class TestCaseClass[First, Middle, La${|}st]()
       |
       |  class Test {
       |    def test1 = new TestCaseClass[Int, String, Boolean]
       |    def test2 = TestCaseClass[Int, String, Boolean]()
       |    def test3 = TestCaseClass.apply[Int, String, Boolean]()
       |    def test4 = tests.safe.delete.TestCaseClass.apply[Int, String, Boolean]()
       |  }
       |}""".stripMargin,
    """package tests.safe.delete {
      |  case class TestCaseClass[First, Middle]()
      |
      |  class Test {
      |    def test1 = new TestCaseClass[Int, String]
      |    def test2 = TestCaseClass[Int, String]()
      |    def test3 = TestCaseClass.apply[Int, String]()
      |    def test4 = tests.safe.delete.TestCaseClass.apply[Int, String]()
      |  }
      |}""".stripMargin,
    wrapTextInClass = false,
  )

  def test_object_apply_call(): Unit = doSafeDeleteTest(
    s"""
       |object Test {
       |  def apply[Ty${|}pe](i: Int) = {}
       |}
       |
       |def test1 = Test(42)
       |def test2 = Test[Int](42)
       |def test3 = Test.apply(42)
       |def test4 = Test.apply[Int](42)
       |""".stripMargin,
    """
      |object Test {
      |  def apply(i: Int) = {}
      |}
      |
      |def test1 = Test(42)
      |def test2 = Test(42)
      |def test3 = Test.apply(42)
      |def test4 = Test.apply(42)
      |""".stripMargin,
  )

  def test_type_param_in_extends(): Unit = doSafeDeleteTest(
    s"""
       |trait Foo[T${|}T]
       |
       |case class Test[A]() extends Foo[A]
       |""".stripMargin,
    """
      |trait Foo
      |
      |case class Test[A]() extends Foo
      |""".stripMargin,
  )

  def test_annotated_type_param(): Unit = doSafeDeleteTest(
    s"""
       |class Foo[T](t: T)
       |case class Test[@specialized(Boolean, Int, AnyRef) T${|}T]()
       |
       |def test1[Type]() = Test[Type]()
       |def test2 = Some(new Foo(Test[Int]()))
       |""".stripMargin,
    """
      |class Foo[T](t: T)
      |case class Test()
      |
      |def test1[Type]() = Test()
      |def test2 = Some(new Foo(Test()))
      |""".stripMargin,
  )

  def test_type_param_with_type_bound(): Unit = doSafeDeleteTest(
    s"""
       |case class Test[T, U${|}U <: Int]()
       |
       |def test = Test[String, Byte]()
       |""".stripMargin,
    """
      |case class Test[T]()
      |
      |def test = Test[String]()
      |""".stripMargin,
  )

  def test_type_param_with_context_bound(): Unit = doSafeDeleteTest(
    s"""
       |case class Test[AA, B${|}B : Ordered, CC]()
       |
       |def test = Test[String, Byte, Char]()
       |""".stripMargin,
    """
      |case class Test[AA, CC]()
      |
      |def test = Test[String, Char]()
      |""".stripMargin,
  )

  def test_nested_type_arguments(): Unit = doSafeDeleteTest(
    s"""
       |class Foo[T${|}T]
       |
       |class Bar[A, B, C](b: B)
       |
       |def test1: Foo[Int] = ???
       |def test2[T <: Foo[T]]: Option[T] = ???
       |def test3: Option[Foo[Char]] = ???
       |def test4: Option[Bar[String, Foo[Char], Byte]] = ???
       |def test5: Option[Foo[Bar[Int, Float, Double]]] = ???
       |def test6: Option[Foo[Bar[Int, Char, Foo[Foo[Boolean]]]]] = ???
       |""".stripMargin,
    """
      |class Foo
      |
      |class Bar[A, B, C](b: B)
      |
      |def test1: Foo = ???
      |def test2[T <: Foo]: Option[T] = ???
      |def test3: Option[Foo] = ???
      |def test4: Option[Bar[String, Foo, Byte]] = ???
      |def test5: Option[Foo] = ???
      |def test6: Option[Foo] = ???
      |""".stripMargin,
  )

  def test_dependencies(): Unit = doSafeDeleteTest(
    s"""
       |case class Foo[T${|}T](t: TT) { // 1. t
       |  def foo: TT = ??? // 2. foo
       |  def bar(i: Int, t2: TT): TT = ??? // 3. t2; 4. bar(...)
       |}
       |
       |def test[T]: T = Foo[T](null.asInstanceOf[T]).foo
       |""".stripMargin,
    """
      |case class Foo(t: TT) { // 1. t
      |  def foo: TT = ??? // 2. foo
      |  def bar(i: Int, t2: TT): TT = ??? // 3. t2; 4. bar(...)
      |}
      |
      |def test[T]: T = Foo(null.asInstanceOf[T]).foo
      |""".stripMargin,
    expectedUnsafeDeletions = 4
  )

  def test_patterns(): Unit = doSafeDeleteTest(
    s"""
       |case class Foo[T${|}T]()
       |
       |def test = {
       |  val foos = List(Foo[Int]())
       |  for {
       |    foo: Foo[Int] <- foos
       |  } {}
       |}
       |""".stripMargin,
    """
      |case class Foo()
      |
      |def test = {
      |  val foos = List(Foo())
      |  for {
      |    foo: Foo <- foos
      |  } {}
      |}
      |""".stripMargin,
  )

}
