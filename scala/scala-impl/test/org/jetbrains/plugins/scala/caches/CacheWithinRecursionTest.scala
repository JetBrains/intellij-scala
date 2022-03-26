package org.jetbrains.plugins.scala
package caches

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers

class CacheWithinRecursionTest extends ScalaLightCodeInsightFixtureTestAdapter with CacheTestUtils  with AssertionMatchers { self =>
  import self.{CachedRecursiveFunction => Func}

  def test_simple(): Unit = {
    val a = Func("a")

    a() shouldBe "a()"
    a() shouldBe "@a()"
  }


  def test_non_rec_inner(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b ~> c

    a() shouldBe "a(b(c()))"
    c() shouldBe "@c()"
    b() shouldBe "@b(c())"
  }

  def test_non_rec_successive(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b
    a ~> c

    a() shouldBe "a(b()+c())"
    c() shouldBe "@c()"
    b() shouldBe "@b()"
  }

  def test_self_call(): Unit = {
    val a = Func("a")

    a ~> a

    a() shouldBe "a(#a)"
    a() shouldBe "@a(#a)"
  }

  def test_loop_2(): Unit = {
    val a = Func("a")
    val b = Func("b")

    // simple loop
    b ~> a ~> b

    a() shouldBe "a(b(#a))"
    b() shouldBe "b(@a(b(#a)))"

    b() shouldBe "@b(@a(b(#a)))"
    a() shouldBe "@a(b(#a))"
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

    a() shouldBe "a(b(c(#a)))"
    b() shouldBe "b(c(@a(b(c(#a)))))"
    c() shouldBe "@c(@a(b(c(#a))))"

    b() shouldBe "@b(c(@a(b(c(#a)))))"
    a() shouldBe "@a(b(c(#a)))"
  }

  def test_loop_3_cb(): Unit = {
    val (a, b, c) = make3Loop()

    a() shouldBe "a(b(c(#a)))"
    c() shouldBe "c(@a(b(c(#a))))"
    b() shouldBe "b(@c(@a(b(c(#a)))))"

    b() shouldBe "@b(@c(@a(b(c(#a)))))"
    c() shouldBe "@c(@a(b(c(#a))))"
    a() shouldBe "@a(b(c(#a)))"
  }

  def test_outside_of_recursion(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b ~> c ~> b

    a() shouldBe "a(b(c(#b)))"
    // after the call to a, a and b should be cached but not c
    b() shouldBe "@b(c(#b))"
    a() shouldBe "@a(b(c(#b)))"
    c() shouldBe "c(@b(c(#b)))"
  }

  def test_before_recursion(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b
    a ~> c ~> c

    a() shouldBe "a(b()+c(#c))"
    b() shouldBe "@b()"
    c() shouldBe "@c(#c)"
    a() shouldBe "@a(b()+c(#c))"
  }


  def test_after_recursion(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b ~> b
    a ~> c

    a() shouldBe "a(b(#b)+c())"
    b() shouldBe "@b(#b)"
    c() shouldBe "@c()"
    a() shouldBe "@a(b(#b)+c())"
  }

  def test_inside_of_recursion_but_before_recursive_branch(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b
    a ~> c ~> a

    a() shouldBe "a(b()+c(#a))"
    // b should be cached but not c
    b() shouldBe "@b()"
    c() shouldBe "c(@a(b()+c(#a)))"

    a() shouldBe "@a(b()+c(#a))"
    c() shouldBe "@c(@a(b()+c(#a)))"
  }

  def test_inside_of_recursion_but_after_recursive_branch(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b ~> a
    a ~> c

    a() shouldBe "a(b(#a)+c())"
    // c should be cached but not b
    c() shouldBe "@c()"
    b() shouldBe "b(@a(b(#a)+c()))"

    a() shouldBe "@a(b(#a)+c())"
    b() shouldBe "@b(@a(b(#a)+c()))"
  }

  def test_inside_of_recursion_but_after_recursive_branch_2(): Unit = {
    val a = Func("a")
    val a2 = Func("a2")
    val b = Func("b")
    val c = Func("c")

    a ~> a2 ~> b ~> a
         a2 ~> c

    a() shouldBe "a(a2(b(#a)+c()))"
    // c should be cached but not b
    c() shouldBe "@c()"
    a2() shouldBe "a2(b(@a(a2(b(#a)+c())))+@c())"
    b() shouldBe "@b(@a(a2(b(#a)+c())))"

    a() shouldBe "@a(a2(b(#a)+c()))"
    a2() shouldBe "@a2(b(@a(a2(b(#a)+c())))+@c())"
  }

  def test_local_cache(): Unit = {
    val a = Func("a")
    val b = Func("b")

    b ~> a
    // a calls b twice
    a ~> b
    a ~> b

    // b is cached locally inside of a, but has to be recomputed when leaving the recursion
    a() shouldBe "a(b(#a)+@@b(#a))"
    b() shouldBe "b(@a(b(#a)+@@b(#a)))"
  }

  def test_local_cache_reset(): Unit = {
    val a = Func("a")
    val b = Func("b")
    val c = Func("c")

    a ~> b ~> a
         b ~> c ~> b
         b ~> c
    a ~> c

    // d is locally cached when inside the recursion of c but is not cached outside of it
    val expectA = s"a(b(#a+c(#b)+@@c(#b))+c(@@b(#a+c(#b)+@@c(#b))))"
    a() shouldBe expectA
    val expectB = s"b(@$expectA+c(#b)+@@c(#b))"
    b() shouldBe expectB
    c() shouldBe s"c(@$expectB)"
  }
}
