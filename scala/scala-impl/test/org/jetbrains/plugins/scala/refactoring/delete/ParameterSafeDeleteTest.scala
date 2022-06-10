package org.jetbrains.plugins.scala
package refactoring
package delete

class ParameterSafeDeleteTest extends ScalaSafeDeleteTestBase {

  def test_unreferenced(): Unit = doSafeDeleteTest(
    s"""
       |def test(par${|}am: Int) = {
       |
       |}
       |""".stripMargin,
    """
      |def test() = {
      |
      |}
      |""".stripMargin
  )

  def test_unreferenced_generic(): Unit = doSafeDeleteTest(
    s"""
       |def test[Type](par${|}am: Int) = {
       |
       |}
       |""".stripMargin,
    """
      |def test[Type]() = {
      |
      |}
      |""".stripMargin
  )

  def test_referenced_java(): Unit = doSafeDeleteTest(
    s"""
       |void test(int before, int par${|}am, int after) {}
       |
       |void test2() {
       |  test(1, 2, 3)
       |}
       |""".stripMargin,
    """
      |void test(int before, int after) {}
      |
      |void test2() {
      |  test(1, 3)
      |}
      |""".stripMargin,
    "java"
  )

  def test_unreferenced_between_multiple(): Unit = doSafeDeleteTest(
    s"""
       |def test(param1: String, par${|}am: Int, param2: Boolean) = {
       |
       |}
       |""".stripMargin,
    """
      |def test(param1: String, param2: Boolean) = {
      |
      |}
      |""".stripMargin
  )

  def test_unreferenced_between_multiple_in_different_lines(): Unit = doSafeDeleteTest(
    s"""
       |def test(param1: String,
       |         par${|}am: Int,
       |         param2: Boolean) = {
       |
       |}
       |""".stripMargin,
    """
      |def test(param1: String,
      |         param2: Boolean) = {
      |
      |}
      |""".stripMargin
  )

  def test_unreferenced_between_multiple_in_different_lines2(): Unit = doSafeDeleteTest(
    s"""
       |def test(param1: String,
       |         par${|}am: Int, after: Float,
       |         param2: Boolean) = {
       |
       |}
       |""".stripMargin,
    """
      |def test(param1: String, after: Float,
      |         param2: Boolean) = {
      |
      |}
      |""".stripMargin
  )

  def test_delete_last_param(): Unit = doSafeDeleteTest(
    s"""
       |def test(aa: Int, b${|}b: Int): Unit = ()
       |
       |test(1, 2)
       |""".stripMargin,
    """
      |def test(aa: Int): Unit = ()
      |
      |test(1)
      |""".stripMargin
  )

  def test_delete_trailing_comma(): Unit = doSafeDeleteTest(
    s"""
       |def test(aa: Int,
       |         b${|}b: Int,
       |): Unit = ()
       |
       |test(1, 2)
       |""".stripMargin,
    """
      |def test(aa: Int,
      |        ): Unit = ()
      |
      |test(1)
      |""".stripMargin
  )

  def test_second_param_clause(): Unit = doSafeDeleteTest(
    s"""
       |def test(blub: Int)
       |        (param1: String,
       |         par${|}am: Int, after: Float,
       |         param2: Boolean) = ()
       |
       |test(1)
       |    ("",
       |     1.1,
       |     true)
       |""".stripMargin,
    """
      |def test(blub: Int)
      |        (param1: String, after: Float,
      |         param2: Boolean) = ()
      |
      |test(1)
      |    ("",
      |     1.1,
      |     true)
      |""".stripMargin
  )

  def test_second_param_clause_with_single_param(): Unit = doSafeDeleteTest(
    s"""
       |def test(blub: Int)
       |        (par${|}am: Int) = ()
       |
       |test(1)(2)
       |""".stripMargin,
    """
      |def test(blub: Int) = ()
      |
      |test(1)
      |""".stripMargin
  )

  def test_multi_param_clause_delete_first(): Unit = doSafeDeleteTest(
    s"""
       |def test(a${|}a: Int)(bb: Int)(cc: Int) = ()
       |
       |test(1)(2)(3)
       |""".stripMargin,
    """
      |def test(bb: Int)(cc: Int) = ()
      |
      |test(2)(3)
      |""".stripMargin
  )

  def test_multi_param_clause_delete_first_generic(): Unit = doSafeDeleteTest(
    s"""
       |def test[Type](a${|}a: Int)(bb: Int)(cc: Int) = ()
       |
       |test[Int](1)(2)(3)
       |""".stripMargin,
    """
      |def test[Type](bb: Int)(cc: Int) = ()
      |
      |test[Int](2)(3)
      |""".stripMargin
  )

  def test_multi_param_clause_delete_middle(): Unit = doSafeDeleteTest(
    s"""
       |def test(aa: Int)(b${|}b: Int)(cc: Int) = ()
       |
       |test(1)(2)(3)
       |""".stripMargin,
    """
      |def test(aa: Int)(cc: Int) = ()
      |
      |test(1)(3)
      |""".stripMargin
  )

  def test_multi_param_clause_delete_middle_generic(): Unit = doSafeDeleteTest(
    s"""
       |def test[Type](aa: Int)(b${|}b: Int)(cc: Int) = ()
       |
       |test[String](1)(2)(3)
       |""".stripMargin,
    """
      |def test[Type](aa: Int)(cc: Int) = ()
      |
      |test[String](1)(3)
      |""".stripMargin
  )

  def test_multi_param_clause_delete_last(): Unit = doSafeDeleteTest(
    s"""
       |def test(aa: Int)(bb: Int)(c${|}c: Int) = ()
       |
       |test(1)(2)(3)
       |""".stripMargin,
    """
      |def test(aa: Int)(bb: Int) = ()
      |
      |test(1)(2)
      |""".stripMargin
  )

  def test_multi_param_clause_delete_last_generic(): Unit = doSafeDeleteTest(
    s"""
       |def test[Type](aa: Int)(bb: Int)(c${|}c: Int) = ()
       |
       |test[Boolean](1)(2)(3)
       |""".stripMargin,
    """
      |def test[Type](aa: Int)(bb: Int) = ()
      |
      |test[Boolean](1)(2)
      |""".stripMargin
  )

  def test_multi_class_param_clause_delete_first(): Unit = doSafeDeleteTest(
    s"""
       |class Test(a${|}a: Int)(bb: Int)(cc: Int)
       |
       |new Test(1)(2)(3)
       |""".stripMargin,
    """
      |class Test(bb: Int)(cc: Int)
      |
      |new Test(2)(3)
      |""".stripMargin
  )

  def test_multi_class_param_clause_delete_first_generic(): Unit = doSafeDeleteTest(
    s"""
       |class Test[Type](a${|}a: Int)(bb: Int)(cc: Int)
       |
       |new Test[Int](1)(2)(3)
       |""".stripMargin,
    """
      |class Test[Type](bb: Int)(cc: Int)
      |
      |new Test[Int](2)(3)
      |""".stripMargin
  )

  def test_multi_class_param_clause_delete_middle(): Unit = doSafeDeleteTest(
    s"""
       |class Test(aa: Int)(b${|}b: Int)(cc: Int)
       |
       |new Test(1)(2)(3)
       |""".stripMargin,
    """
      |class Test(aa: Int)(cc: Int)
      |
      |new Test(1)(3)
      |""".stripMargin
  )

  def test_multi_class_param_clause_delete_middle_generic(): Unit = doSafeDeleteTest(
    s"""
       |class Test[Type](aa: Int)(b${|}b: Int)(cc: Int)
       |
       |new Test[Int](1)(2)(3)
       |""".stripMargin,
    """
      |class Test[Type](aa: Int)(cc: Int)
      |
      |new Test[Int](1)(3)
      |""".stripMargin
  )

  def test_multi_class_param_clause_delete_last(): Unit = doSafeDeleteTest(
    s"""
       |class Test(aa: Int)(bb: Int)(c${|}c: Int)
       |
       |new Test(1)(2)(3)
       |""".stripMargin,
    """
      |class Test(aa: Int)(bb: Int)
      |
      |new Test(1)(2)
      |""".stripMargin
  )

  def test_multi_class_param_clause_delete_last_generic(): Unit = doSafeDeleteTest(
    s"""
       |class Test[Type](aa: Int)(bb: Int)(c${|}c: Int)
       |
       |new Test[Byte](1)(2)(3)
       |""".stripMargin,
    """
      |class Test[Type](aa: Int)(bb: Int)
      |
      |new Test[Byte](1)(2)
      |""".stripMargin
  )

  def test_unreferenced_implicit_param(): Unit = doSafeDeleteTest(
    s"""
       |def test(implicit bl${|}ub: Int) = ()
       |""".stripMargin,
    """
      |def test() = ()
      |""".stripMargin
  )

  def test_unreferenced_implicit_param_generic(): Unit = doSafeDeleteTest(
    s"""
       |def test[Type](implicit bl${|}ub: Int) = ()
       |""".stripMargin,
    """
      |def test[Type]() = ()
      |""".stripMargin
  )

  def test_unreferenced_second_implicit_param(): Unit = doSafeDeleteTest(
    s"""
       |def test(blub: Int)(implicit bl${|}ub: Int) = ()
       |""".stripMargin,
    """
      |def test(blub: Int) = ()
      |""".stripMargin
  )

  def test_unreferenced_second_implicit_param_generic(): Unit = doSafeDeleteTest(
    s"""
       |def test[Type](blub: Int)(implicit bl${|}ub: Int) = ()
       |""".stripMargin,
    """
      |def test[Type](blub: Int) = ()
      |""".stripMargin
  )

  def test_class_param(): Unit = doSafeDeleteTest(
    s"""
       |class AAA(bl${|}ub: Int)
       |
       |new AAA(3)
       |""".stripMargin,
    s"""
       |class AAA()
       |
       |new AAA()
       |""".stripMargin,
  )

  def test_class_param_generic(): Unit = doSafeDeleteTest(
    s"""
       |class AAA[Type](bl${|}ub: Int)
       |
       |new AAA[Int](3)
       |""".stripMargin,
    s"""
       |class AAA[Type]()
       |
       |new AAA[Int]()
       |""".stripMargin,
  )

  def test_multi_class_param(): Unit = doSafeDeleteTest(
    s"""
       |class AAA(before: Int,
       |          bl${|}ub: Int,
       |          after: Int)
       |
       |new AAA(
       |  1,
       |  2,
       |  3
       |)
       |""".stripMargin,
    s"""
       |class AAA(before: Int,
       |          after: Int)
       |
       |new AAA(
       |  1,
       |  3
       |)
       |""".stripMargin,
  )

  def test_case_class_param(): Unit = doSafeDeleteTest(
    s"""
       |case class AAA(bl${|}ub: Int)
       |
       |AAA(3)
       |""".stripMargin,
    s"""
       |case class AAA()
       |
       |AAA()
       |""".stripMargin,
  )

  def test_case_class_param_generic(): Unit = doSafeDeleteTest(
    s"""
       |case class AAA[Type](bl${|}ub: Int)
       |
       |AAA[Boolean](3)
       |""".stripMargin,
    s"""
       |case class AAA[Type]()
       |
       |AAA[Boolean]()
       |""".stripMargin,
  )

  def test_multi_case_class_param(): Unit = doSafeDeleteTest(
    s"""
       |case class AAA(before: Int,
       |               bl${|}ub: Int,
       |               after: Int)
       |
       |AAA(1, 2, 3)
       |""".stripMargin,
    s"""
       |case class AAA(before: Int,
       |               after: Int)
       |
       |AAA(1, 3)
       |""".stripMargin,
  )

  def test_case_class_custom_apply(): Unit = doSafeDeleteTest(
    s"""
       |case class AAA(bl${|}ub: Int)
       |
       |object AAA {
       |  def apply(real: Int) = new AAA(real)
       |}
       |
       |AAA(3)
       |""".stripMargin,
    s"""
       |case class AAA()
       |
       |object AAA {
       |  def apply(real: Int) = new AAA()
       |}
       |
       |AAA(3)
       |""".stripMargin,
  )

  def test_case_class_copy(): Unit = doSafeDeleteTest(
    s"""
       |case class AAA(bl${|}ub: Int)
       |
       |AAA(3).copy()
       |AAA(3).copy(blub = 4)
       |""".stripMargin,
    s"""
       |case class AAA()
       |
       |AAA().copy()
       |AAA().copy()
       |""".stripMargin,
  )

  def test_case_class_copy_generic(): Unit = doSafeDeleteTest(
    s"""
       |case class AAA[Type](bl${|}ub: Int)
       |
       |AAA[Int](3).copy()
       |AAA[Int](3).copy(blub = 4)
       |""".stripMargin,
    s"""
       |case class AAA[Type]()
       |
       |AAA[Int]().copy()
       |AAA[Int]().copy()
       |""".stripMargin,
  )

  def test_case_class_unapply(): Unit = doSafeDeleteTest(
    s"""
       |case class AAA(bl${|}ub: Int, blub2: Boolean)
       |
       |null match {
       |  case AAA(a, b) => ()
       |}
       |""".stripMargin,
    s"""
       |case class AAA(blub2: Boolean)
       |
       |null match {
       |  case AAA(a, b) => ()
       |}
       |""".stripMargin,
    expectedUnsafeDeletions = 1
  )


  def test_case_class_param_with_new(): Unit = doSafeDeleteTest(
    s"""
       |case class AAA(bl${|}ub: Int)
       |
       |new AAA(3)
       |""".stripMargin,
    s"""
       |case class AAA()
       |
       |new AAA()
       |""".stripMargin,
  )

  def test_apply_call(): Unit = doSafeDeleteTest(
    s"""
       |object Test {
       |  def apply(i${|}i: Int) = ()
       |}
       |
       |Test(3)
       |""".stripMargin,
    s"""
       |object Test {
       |  def apply() = ()
       |}
       |
       |Test()
       |""".stripMargin
  )

  def test_apply_call_generic(): Unit = doSafeDeleteTest(
    s"""
       |object Test {
       |  def apply[Type](i${|}i: Int) = ()
       |}
       |
       |Test[Byte](3)
       |""".stripMargin,
    s"""
       |object Test {
       |  def apply[Type]() = ()
       |}
       |
       |Test[Byte]()
       |""".stripMargin
  )

  def test_apply_call_generic_explicit(): Unit = doSafeDeleteTest(
    s"""
       |object Test {
       |  def apply[Type](i${|}i: Int) = ()
       |}
       |
       |Test.apply[Byte](3)
       |""".stripMargin,
    s"""
       |object Test {
       |  def apply[Type]() = ()
       |}
       |
       |Test.apply[Byte]()
       |""".stripMargin
  )

  def test_self_invocation(): Unit = doSafeDeleteTest(
    s"""
       |class Test(i${|}i: Int) {
       |  def this() = this(1)
       |}
       |""".stripMargin,
    s"""
       |class Test() {
       |  def this() = this()
       |}
       |""".stripMargin,
  )

  def test_auxiliary_constructor(): Unit = doSafeDeleteTest(
    s"""
       |class Test {
       |  def this(i${|}i: Int) = this()
       |  def this(a: String) = this(3)
       |}
       |new Test(1)
       |""".stripMargin,
    s"""
       |class Test {
       |  def this() = this()
       |  def this(a: String) = this()
       |}
       |new Test()
       |""".stripMargin
  )

  def test_annotated_param(): Unit = doSafeDeleteTest(
    s"""
       |def test(@Something xx${|}x: Int)
       |""".stripMargin,

    """
      |def test()
      |""".stripMargin
  )

  def test_constant_arg(): Unit = doSafeDeleteTest(
    s"""
       |def test(par${|}am: Int) = ()
       |
       |test(3)
       |""".stripMargin,
    s"""
       |def test() = ()
       |
       |test()
       |""".stripMargin
  )

  def test_constant_arg_multi(): Unit = doSafeDeleteTest(
    s"""
       |def test(before: Int, par${|}am: Int, after: Int) = ()
       |
       |test(1, 2, 3)
       |""".stripMargin,
    s"""
       |def test(before: Int, after: Int) = ()
       |
       |test(1, 3)
       |""".stripMargin
  )

  def test_recursive(): Unit = doSafeDeleteTest(
    s"""
       |def test(par${|}am: Int) {
       |  test(param)
       |}
       |""".stripMargin,
    s"""
       |def test() {
       |  test()
       |}
       |""".stripMargin
  )

  def test_super_call(): Unit = doSafeDeleteTest(
    s"""
       |class A {
       |  def test(a${|}a: Int): Unit = {
       |
       |  }
       |}
       |
       |class B extends A {
       |  def blub(): Unit = {
       |    super.test(3)
       |  }
       |}
       |""".stripMargin,
    s"""
       |class A {
       |  def test(): Unit = {
       |
       |  }
       |}
       |
       |class B extends A {
       |  def blub(): Unit = {
       |    super.test()
       |  }
       |}
       |""".stripMargin
  )

  def test_default_argument(): Unit = doSafeDeleteTest(
    s"""
       |def test(bl${|}ub: Int = 3) = ()
       |""".stripMargin,
    """
      |def test() = ()
      |""".stripMargin
  )

  def test_referenced_default_parameter_with_explicit_argument(): Unit = doSafeDeleteTest(
    s"""
       |def test(before: Int, bl${|}ub: Int = 3, after: Int = 0) = ()
       |test(1, 2, 3)
       |""".stripMargin,
    """
      |def test(before: Int, after: Int = 0) = ()
      |test(1, 3)
      |""".stripMargin
  )

  def test_referenced_default_parameter_without_argument(): Unit = doSafeDeleteTest(
    s"""
       |def test(before: Int, bl${|}ub: Int = 3, after: Int = 0) = ()
       |test(1)
       |""".stripMargin,
    """
      |def test(before: Int, after: Int = 0) = ()
      |test(1)
      |""".stripMargin
  )

  def test_named_parameter(): Unit = doSafeDeleteTest(
    s"""
       |def test(a${|}a: Int, bb: Int) = ()
       |test(bb = 2, aa = 1)
       |""".stripMargin,
    s"""
       |def test(bb: Int) = ()
       |test(bb = 2)
       |""".stripMargin,
  )

  def test_named_parameter_generic(): Unit = doSafeDeleteTest(
    s"""
       |def test[Type](a${|}a: Int, bb: Int) = ()
       |test[Int](bb = 2, aa = 1)
       |""".stripMargin,
    s"""
       |def test[Type](bb: Int) = ()
       |test[Int](bb = 2)
       |""".stripMargin,
  )

  def test_infix_call(): Unit = doSafeDeleteTest(
    s"""
       |object O {
       |  def +(i${|}i: Int) = ()
       |}
       |
       |O + 3
       |""".stripMargin,
    """
      |object O {
      |  def +() = ()
      |}
      |
      |O.+()
      |""".stripMargin
  )

  def test_infix_call_multiple_params(): Unit = doSafeDeleteTest(
    s"""
       |object O {
       |  def +(ll: Int, i${|}i: Int, jj: Int) = ()
       |}
       |
       |O + (1, 2, 3)
       |""".stripMargin,
    """
      |object O {
      |  def +(ll: Int, jj: Int) = ()
      |}
      |
      |O + (1, 3)
      |""".stripMargin
  )
}
