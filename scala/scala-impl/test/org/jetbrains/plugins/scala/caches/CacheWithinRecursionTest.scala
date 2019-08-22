package org.jetbrains.plugins.scala.caches

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class CacheWithinRecursionTest extends ScalaLightCodeInsightFixtureTestAdapter with CacheTestUtils { self =>
  import org.junit.Assert._
  import self.{CachedRecursiveFunction => Func}

  def test_simple(): Unit = {
    val a = Func("a")

    assertEquals("a()", a())
    assertEquals("@a()", a())
  }


  def test_non_rec_inner(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b ~> c

    assertEquals("a(b(c()))", a())
    assertEquals("@c()", c())
    assertEquals("@b(c())", b())
  }

  def test_non_rec_successive(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b
    a ~> c

    assertEquals("a(b()+c())", a())
    assertEquals("@c()", c())
    assertEquals("@b()", b())
  }

  def test_self_call(): Unit = {
    val a = Func("a")

    a ~> a

    assertEquals("a(#a)", a())
    assertEquals("@a(#a)", a())
  }

  def test_loop_2(): Unit = {
    val a = Func("a")
    val b = Func("b")

    // simple loop
    b ~> a ~> b

    assertEquals("a(b(#a))", a())
    assertEquals("b(@a(b(#a)))", b())

    assertEquals("@b(@a(b(#a)))", b())
    assertEquals("@a(b(#a))", a())
  }


  def make3Loop(): (Func, Func, Func) = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b ~> c ~> a

    (a, b, c)
  }

  def test_loop_3_bc(): Unit = {
    val (a, b, c) = make3Loop()

    assertEquals("a(b(c(#a)))", a())
    assertEquals("b(c(@a(b(c(#a)))))", b())
    assertEquals("@c(@a(b(c(#a))))", c())

    assertEquals("@b(c(@a(b(c(#a)))))", b())
    assertEquals("@a(b(c(#a)))", a())
  }

  def test_loop_3_cb(): Unit = {
    val (a, b, c) = make3Loop()

    assertEquals("a(b(c(#a)))", a())
    assertEquals("c(@a(b(c(#a))))", c())
    assertEquals("b(@c(@a(b(c(#a)))))", b())

    assertEquals("@b(@c(@a(b(c(#a)))))", b())
    assertEquals("@c(@a(b(c(#a))))", c())
    assertEquals("@a(b(c(#a)))", a())
  }

  def test_outside_of_recursion(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b ~> c ~> b

    assertEquals("a(b(c(#b)))", a())
    // after the call to a, a and be should be cached but not c
    assertEquals("@b(c(#b))", b())
    assertEquals("@a(b(c(#b)))", a())
    assertEquals("c(@b(c(#b)))", c())
  }

  def test_before_recursion(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b
    a ~> c ~> c

    assertEquals("a(b()+c(#c))", a())
    assertEquals("@b()", b())
    assertEquals("@c(#c)", c())
    assertEquals("@a(b()+c(#c))", a())
  }


  def test_after_recursion(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b ~> b
    a ~> c

    assertEquals("a(b(#b)+c())", a())
    assertEquals("@b(#b)", b())
    assertEquals("@c()", c())
    assertEquals("@a(b(#b)+c())", a())
  }

  def test_inside_of_recursion_but_before_recursive_branch(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b
    a ~> c ~> a

    assertEquals("a(b()+c(#a))", a())
    // b should be cached but not c
    assertEquals("@b()", b())
    assertEquals("c(@a(b()+c(#a)))", c())

    assertEquals("@a(b()+c(#a))", a())
    assertEquals("@c(@a(b()+c(#a)))", c())
  }

  def test_inside_of_recursion_but_after_recursive_branch(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b ~> a
    a ~> c

    assertEquals("a(b(#a)+c())", a())
    // c should be cached but not b
    assertEquals("@c()", c())
    assertEquals("b(@a(b(#a)+c()))", b())

    assertEquals("@a(b(#a)+c())", a())
    assertEquals("@b(@a(b(#a)+c()))", b())
  }

  def test_inside_of_recursion_but_after_recursive_branch_2(): Unit = {
    val a = Func("a")
    val a2 = Func("a2")
    val b = Func("b")
    val c = Func("c")

    a ~> a2 ~> b ~> a
    a2 ~> c

    assertEquals("a(a2(b(#a)+c()))", a())
    // c should be cached but not b
    assertEquals("@c()", c())
    assertEquals("a2(b(@a(a2(b(#a)+c())))+@c())", a2())
    assertEquals("@b(@a(a2(b(#a)+c())))", b())

    assertEquals("@a(a2(b(#a)+c()))", a())
    assertEquals("@a2(b(@a(a2(b(#a)+c())))+@c())", a2())
  }
}
