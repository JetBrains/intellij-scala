package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.positive

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBePrivateInspectionTestBase

class AccessCanBePrivateInspectionTest extends ScalaAccessCanBePrivateInspectionTestBase {

  private val AllowAdditionalHighlights = false

  def test_method(): Unit =
    checkTextHasError(s"private class Foo { def ${START}bar$END = {}; def foo = bar }", AllowAdditionalHighlights)

  def test_val(): Unit =
    checkTextHasError(s"private class Foo { val ${START}bar$END = 42; def foo = bar }", AllowAdditionalHighlights)

  def test_var(): Unit =
    checkTextHasError(s"private class Foo { var ${START}bar$END = 42; def foo = bar }", AllowAdditionalHighlights)

  def test_new_template_definition(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; new B }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_val(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private val c: B = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_var(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private var c: B = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_def_return_type(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c: B = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_this_qualified_def_return_type(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private[this] def c: B = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_nested_def_return_type1(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c: Option[B] = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_nested_def_return_type2(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c: Option[Seq[B]] = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_nested_def_return_type3(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c: Either[B, String] = ??? }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_def_param(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def foo(b: B) = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_nested_def_param1(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c(d: Option[B]) = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_nested_def_param2(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c(d: Option[Seq[B]]) = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_nested_def_param3(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def c(d: Either[B, String]) = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_def_type_param(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private def foo[T <: B]() = () }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_inheritance(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private class C extends B }", AllowAdditionalHighlights)

  def test_usage_within_companion(): Unit = checkTextHasError(
    s"""private class Foo { val ${START}a$END = 42 }
       |private object Foo { new Foo().a }
       |private class Bar { Bar.b }
       |private object Bar { val ${START}b$END = 42 }
       |""".stripMargin, AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_rhs_usage(): Unit =
    checkTextHasError(s"object A { object ${START}B$END; def foo() { println(B) } }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_local_method_member(): Unit = checkTextHasError(
    s"object A { class ${START}B$END; def foo() { def bar() = { new B } } }",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_local_value_member(): Unit = checkTextHasError(
    s"object A { class ${START}B$END; val foo = { val bar = { new B } } }",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_local_method(): Unit = checkTextHasError(
    s"object A { class ${START}B$END; def foo = { def bar = { Some(new B); () }; bar } }",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_nested_private_method(): Unit = checkTextHasError(
    s"object A { trait ${START}B$END; object C { private def bar: B = ??? } }",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_primary_constructor1(): Unit = checkTextHasError(
    s"private object A { class ${START}B$END }; class A (b: A.B)",
    AllowAdditionalHighlights
  )
  def test_no_need_to_prevent_escaping_via_primary_constructor2(): Unit = checkTextHasError(
    s"private object A { class ${START}B$END }; class A (private val b: A.B)",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_primary_constructor3(): Unit = checkTextHasError(
    s"private object A { class ${START}B$END }; class A private (b: A.B)",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_primary_constructor4(): Unit = checkTextHasError(
    s"private object A { class ${START}B$END }; class A private (b: A.B = ???)",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_primary_constructor5(): Unit = checkTextHasError(
    s"private object A { class ${START}B$END }; class A private (private val b: A.B)",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_primary_constructor6(): Unit = checkTextHasError(
    s"private object A { class ${START}B$END }; class A private (private val b: A.B = ???)",
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_type_alias(): Unit =
    checkTextHasError(s"object A { class ${START}B$END; private type C = B }", AllowAdditionalHighlights)

  def test_macro_impl_that_is_not_referenced_by_a_macro_definition(): Unit = checkTextHasError(
      s"""import scala.reflect.macros.whitebox
         |object Foo {
         |  def ${START}defMacroImpl$END(c: whitebox.Context): c.Tree = {
         |    import c.universe._
         |    q"1"
         |  }
         |  def foo(): Unit = defMacroImpl(null)
         |}
         |""".stripMargin, AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_projection_type_in_companion1(): Unit = checkTextHasError(
    s"private object A { object ${START}B$END { class C } }; class A { private def foo: A.B.C = ???}",
    AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_projection_type_in_companion2(): Unit = checkTextHasError(
    s"private object A { object ${START}B$END { object C { class D } } }; class A { private def foo: A.B.C.D = ???}",
    AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_projection_type1(): Unit = checkTextHasError(
    s"""object A {
       |  object ${START}B$END {
       |    object C {
       |      object D {
       |        class E
       |      }
       |      println(D)
       |    }
       |    println(C)
       |  }
       |  private def foo: A.B.C.D.E = ???
       |  println(B)
       |}
       |""".stripMargin,
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_projection_type2(): Unit = checkTextHasError(
    s"""object A {
       |  object ${START}B$END {
       |    object ${START}C$END {
       |      object D {
       |        class E
       |      }
       |      println(D)
       |    }
       |    private def foo: A.B.C.D.E = ???
       |    println(C)
       |  }
       |  println(B)
       |}
       |""".stripMargin,
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_projection_type3(): Unit = checkTextHasError(
    s"""object A {
       |  object ${START}B$END {
       |    object ${START}C$END {
       |      object ${START}D$END {
       |        class E
       |      }
       |      private def foo: A.B.C.D.E = ???
       |      println(D)
       |    }
       |    println(C)
       |  }
       |  println(B)
       |}
       |""".stripMargin,
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_projection_type4(): Unit = checkTextHasError(
    s"""object A {
       |  object ${START}B$END {
       |    object ${START}C$END {
       |      object ${START}D$END {
       |        class ${START}E$END
       |        private def foo: A.B.C.D.E = ???
       |      }
       |      println(D)
       |    }
       |    println(C)
       |  }
       |  println(B)
       |}
       |""".stripMargin,
    AllowAdditionalHighlights
  )

  def test_no_need_to_prevent_escaping_via_path_dependent_type(): Unit =
    checkTextHasError(s"class A { private class B; val ${START}a$END = new A; private def b = new a.B }", AllowAdditionalHighlights)

  def test_no_need_to_prevent_escaping_via_this_type(): Unit =
    checkTextHasError(
      s"""object AA {
         |  class Foo { object Bar }
         |  class ${START}BB$END extends Foo { val b = Bar }
         |  new BB
         |}
         |""".stripMargin,
        AllowAdditionalHighlights)

  def test_implicit_class_extension_method_used_directly_from_within_itself(): Unit = checkTextHasError(
    s"""object foo {
       |  implicit class IntExt1(i: Int) { self =>
       |    def ${START}addOne$END = i + 1
       |    def addOneCanBePrivate1 = addOne
       |    def addOneCanBePrivate2 = addOne.bar
       |    def addOneCanBePrivate3 = this.addOne
       |    def addOneCanBePrivate4 = self.addOne
       |  }
       |  implicit class IntExt2(i: Int) { def bar = 42 }
       |}
       |""".stripMargin, AllowAdditionalHighlights)

  def test_inner_class_accessed_by_companion(): Unit =
    checkTextHasError(s"private object A { class ${START}B$END }; class A { private def foo: A.B = ??? }", AllowAdditionalHighlights)

  def test_imported_companion_object_method(): Unit =
    checkTextHasError(s"class A { import A._; println(a) }; private object A { def ${START}a$END = 1 }", AllowAdditionalHighlights)

  def test_indirect_companion_object_method(): Unit =
    checkTextHasError(s"class A { private def foo = A; println(foo.a) }; private object A { def ${START}a$END = 1 }", AllowAdditionalHighlights)

  def test_type_alias(): Unit =
    checkTextHasError(s"object A { type ${START}B$END = Int; def foo: B = 1", AllowAdditionalHighlights)

  def test_multiple_val_assignment(): Unit = checkTextHasError(
    s"object Foo { val ${START}x, y$END = 1; println(x + y) }",
    AllowAdditionalHighlights
  )

  def test_untupled_val_assignment(): Unit = checkTextHasError(
    s"object Foo { val $START(x, y)$END = (1, 2); println(x + y) }",
    AllowAdditionalHighlights
  )

  def test_top_level_definition1(): Unit =
    checkTextHasError(s"class ${START}A$END; object A { private def a(): A = new A() }", AllowAdditionalHighlights)

  def test_top_level_definition2(): Unit =
    checkTextHasError(
      s"""object ${START}B$END { private val b1 = 1 }
         |class B { val b2 = B.b1 + 1 }
         |object A { new B().b2 }
         |""".stripMargin, AllowAdditionalHighlights)

  def test_top_level_definition3(): Unit =
    checkTextHasError(
      s"""class ${START}B$END { private val b1 = 1 }
         |object B { val b2 = new B().b1 + 1 }
         |object A { B.b2 }
         |""".stripMargin, AllowAdditionalHighlights)
}
