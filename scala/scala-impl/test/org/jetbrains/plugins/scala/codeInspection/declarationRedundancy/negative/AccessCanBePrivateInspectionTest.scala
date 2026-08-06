package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.negative

import org.jetbrains.plugins.scala.codeInspection.declarationRedundancy.ScalaAccessCanBePrivateInspectionTestBase

final class AccessCanBePrivateInspectionTest extends ScalaAccessCanBePrivateInspectionTestBase {

  def test_val(): Unit =
    checkTextHasNoErrors("private class A { val foo = 42 }; private class B { new A().foo }")

  def test_var(): Unit =
    checkTextHasNoErrors("private class A { var foo = 42 }; private class B { new A().foo }")

  def test_method(): Unit =
    checkTextHasNoErrors("private class A { def foo = {} }; private class B { new A().foo }")

  def test_class_used_by_public_class_in_other_file(): Unit = {
    val file = myFixture.addFileToProject("B.scala", "class B extends A")
    myFixture.openFileInEditor(file.getVirtualFile)
    val code = "class A"
    checkTextHasNoErrors(code)
  }

  def test_trait_used_by_public_class_in_other_file(): Unit = {
    val file = myFixture.addFileToProject("B.scala", "class B extends A")
    myFixture.openFileInEditor(file.getVirtualFile)
    val code = "trait A"
    checkTextHasNoErrors(code)
  }

  def test_object_used_by_public_class_in_other_file(): Unit = {
    val file = myFixture.addFileToProject("B.scala", "class B { A }")
    myFixture.openFileInEditor(file.getVirtualFile)
    val code = "object A"
    checkTextHasNoErrors(code)
  }

  def test_declaration_that_is_used_both_in_local_and_nonlocal_scope(): Unit = {
    val file = myFixture.addFileToProject("B.scala", "class B extends A { foo() }")
    myFixture.openFileInEditor(file.getVirtualFile)
    val code = "class A { def foo() = { foo() } }"
    checkTextHasNoErrors(code)
  }

  def test_local_method_members_are_skipped(): Unit =
    checkTextHasNoErrors("private class A { private def foo() = { def bar() = {} } }")

  def test_local_var_val_members_are_skipped(): Unit =
    checkTextHasNoErrors("private class A { private def foo() = { var x = 42; val y = 42; } }")

  def test_unused_declarations_are_skipped(): Unit =
    checkTextHasNoErrors("private class A { def foo() = {} }")

  def test_prevent_escaping_via_val(): Unit =
    checkTextHasNoErrors("object A { class B; val c: B = ??? }")

  def test_prevent_escaping_via_var(): Unit =
    checkTextHasNoErrors("object A { class B; var c: B = ??? }")

  def test_prevent_escaping_via_def_return_type(): Unit =
    checkTextHasNoErrors("object A { class B; def c: B = ??? }")

  def test_prevent_escaping_via_nested_def_return_type1(): Unit =
    checkTextHasNoErrors("object A { class B; def c: Option[B] = ??? }")

  def test_prevent_escaping_via_nested_def_return_type2(): Unit =
    checkTextHasNoErrors("object A { class B; def c: Option[Seq[B]] = ??? }")

  def test_prevent_escaping_via_nested_def_return_type3(): Unit =
    checkTextHasNoErrors("object A { class B; def c: Either[B, String] = ??? }")

  def test_prevent_escaping_via_package_qualified_private_def_return_type(): Unit =
    checkTextHasNoErrors("package a; object A { class B; private[a] def c: B = ??? }")

  def test_prevent_escaping_via_def_param(): Unit =
    checkTextHasNoErrors("object A { class B; def foo(b: B) = () }")

  def test_prevent_escaping_via_nested_def_param1(): Unit =
    checkTextHasNoErrors("object A { class B; def c(d: Option[B]) = () }")

  def test_prevent_escaping_via_nested_def_param2(): Unit =
    checkTextHasNoErrors("object A { class B; def c(d: Option[Seq[B]]) = () }")

  def test_prevent_escaping_via_nested_def_param3(): Unit =
    checkTextHasNoErrors("object A { class B; def c(d: Either[B, String]) = () }")

  def test_prevent_escaping_via_def_type_param(): Unit =
    checkTextHasNoErrors("object A { class B; def foo[T <: B]() = () }")

  def test_prevent_escaping_via_trait_type_param(): Unit =
    checkTextHasNoErrors("object A { class B; trait C[T <: B] }")

  def test_prevent_escaping_via_class_type_param(): Unit =
    checkTextHasNoErrors("object A { class B; class C[T <: B] }")

  def test_prevent_escaping_via_inheritance(): Unit =
    checkTextHasNoErrors("object A { class B; class C extends B }")

  def test_member_of_local_class(): Unit =
    checkTextHasNoErrors("object A { def foo = { class B { val bar = 42; println(bar) } } }")

  def test_member_of_a_surprisingly_nested_local_class(): Unit =
    checkTextHasNoErrors("class A { def foo = { class B { class C { class D { def bar = {}; bar } } } } }")

  def test_package_object(): Unit = {
    myFixture.addFileToProject("zzz.scala", "import foo._")
    checkTextHasNoErrors("package object foo")
  }

  def test_inner_type_definition(): Unit =
    checkTextHasNoErrors("private object A { object B }; object C { println(A.B) }")

  def test_prevent_escaping_via_local_method(): Unit =
    checkTextHasNoErrors(
      """object A {
        |  class B
        |  def foo = { def bar = Some(new B); bar }
        |  def fizz() = { def buzz() = Some(new B); buzz() }
        |}
        |""".stripMargin)

  def test_prevent_escaping_via_companion_class(): Unit =
    checkTextHasNoErrors(
      """object Foo { class Aaa }
        |class Foo { def getAaa() = new Foo.Aaa }
        |""".stripMargin)

  def test_prevent_escaping_via_nested_method(): Unit =
    checkTextHasNoErrors("object A { trait B; object C { def bar: B = ??? } }")

  def test_prevent_parameterized_typedef_escaping(): Unit =
    checkTextHasNoErrors("object A { class B[T]; def foo: B[Int] = ??? }")

  def test_prevent_escaping_via_primary_constructor1(): Unit =
    checkTextHasNoErrors("object A { class B }; class A (val b: A.B)")

  def test_prevent_escaping_via_primary_constructor2(): Unit =
    checkTextHasNoErrors("object A { class B }; class A (b: A.B = ???)")

  def test_prevent_escaping_via_primary_constructor3(): Unit =
    checkTextHasNoErrors("object A { class B }; class A (private val b: A.B = ???)")

  def test_prevent_escaping_via_primary_constructor4(): Unit =
    checkTextHasNoErrors("object A { class B }; class A private (val b: A.B)")

  def test_prevent_escaping_via_primary_constructor5(): Unit =
    checkTextHasNoErrors("object A { class B }; class A private (val b: A.B = ???)")

  def test_prevent_escaping_via_type_alias(): Unit =
    checkTextHasNoErrors("object A { class B; type C = B }")

  def test_prevent_escaping_via_type_alias_type_parameter(): Unit =
    checkTextHasNoErrors("object A { class B; type C[T <: B] = Seq[T] }")

  def test_skip_bean_property(): Unit =
    checkTextHasNoErrors("object A { @scala.beans.BeanProperty var x = 1; println(x) }")

  def test_macro_impl_that_is_referenced_by_a_macro_definition(): Unit = checkTextHasNoErrors(
    s"""import scala.reflect.macros.whitebox
       |import scala.language.experimental.macros
       |object Foo {
       |  def defMacro: Int = macro defMacroImpl
       |  def defMacroImpl(c: whitebox.Context): c.Tree = {
       |    import c.universe._
       |    q"1"
       |  }
       |}
       |""".stripMargin)

  def test_prevent_escaping_via_projection_type_in_companion(): Unit = checkTextHasNoErrors(
    """object A {
      |  object B {
      |    class C
      |  }
      |}
      |
      |class A {
      |  def foo: A.B.C = ???
      |}
      |""".stripMargin)

  def test_prevent_escaping_via_projection_type1(): Unit = checkTextHasNoErrors(
    s"""object A {
       |  object B {
       |    object C {
       |      object D {
       |        class E
       |      }
       |      println(D)
       |    }
       |    println(C)
       |  }
       |  def foo: A.B.C.D.E = ???
       |  println(B)
       |}
       |""".stripMargin
  )

  def test_prevent_escaping_via_projection_type2(): Unit = checkTextHasNoErrors(
    s"""object A {
       |  object B {
       |    object C {
       |      object D {
       |        class E
       |      }
       |      println(D)
       |    }
       |    def foo: A.B.C.D.E = ???
       |    println(C)
       |  }
       |  println(B)
       |}
       |""".stripMargin
  )

  def test_prevent_escaping_via_projection_type3(): Unit = checkTextHasNoErrors(
    s"""object A {
       |  object B {
       |    object C {
       |      object D {
       |        class E
       |      }
       |      def foo: A.B.C.D.E = ???
       |      println(D)
       |    }
       |    println(C)
       |  }
       |  println(B)
       |}
       |""".stripMargin
  )

  def test_prevent_escaping_via_projection_type4(): Unit = checkTextHasNoErrors(
    s"""object A {
       |  object B {
       |    object C {
       |      object D {
       |        class E
       |        def foo: A.B.C.D.E = ???
       |      }
       |      println(D)
       |    }
       |    println(C)
       |  }
       |  println(B)
       |}
       |""".stripMargin
  )

  def test_prevent_escaping_via_path_dependent_type(): Unit =
    checkTextHasNoErrors("class A { class B; val a = new A; def b = new a.B }")

  def test_implicit_class_extension_method_used_indirectly_from_within_itself(): Unit = checkTextHasNoErrors(
    """object bar {
      |  implicit class IntExt1(i: Int) {
      |    def addOne1 = i + 1; def addOne2 = i + 1; def addOne3 = i + 1; def addOne4 = i + 1
      |    def addOne1CanNotBePrivate = i.bar.addOne1
      |    def addOne2CanNotBePrivate = 1.addOne2
      |    def addOne3CanNotBePrivate = Seq(1).map(_.addOne3)
      |    private val anInt = 42
      |    def addOne4CanNotBePrivate = anInt.addOne4
      |  }
      |  implicit class IntExt2(i: Int) { def bar = 42 }
      |}
      |""".stripMargin
  )

  def test_method_defined_in_subclass_of_companion(): Unit = checkTextHasNoErrors(
    """class Foo { def getInt = 1 }
      |class FooBar extends Foo
      |object Foo { new FooBar().getInt }
      |""".stripMargin
  )

  def test_overriding_type_alias(): Unit =
    checkTextHasNoErrors(
      s"""trait A { type Foo }; trait B extends A {}
         |class C extends B { type Foo = Int; val x: Foo = 1 }
         |""".stripMargin)

  // SCL-21690
  def test_patterns(): Unit =
    checkTextHasNoErrors(
      """
        |object G {
        |  val (a: Int, b: Int) = (1, 2)
        |  val (x: Int) = 1
        |}
        |
        |object Use {
        |  println(G.b)
        |  println(G.x)
        |}
        |""".stripMargin
    )

  def test_test_wildcard_pattern(): Unit =
    checkTextHasNoErrors("object A { val (_: Int) = 3 }")
}
