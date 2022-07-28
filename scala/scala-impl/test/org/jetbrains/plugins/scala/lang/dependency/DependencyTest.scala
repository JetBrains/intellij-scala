package org.jetbrains.plugins.scala
package lang
package dependency

import org.intellij.lang.annotations.Language
import org.junit.Assert

class DependencyTest extends base.SimpleTestCase {
  def testClass(): Unit = {
    assertDependenciesAre("""
    object O {
      class C
      classOf[C]
    }
    """, "O.C")
  }

  def testSyntheticClass(): Unit = {
    assertDependenciesAre("""
    object O {
      classOf[Int]
    }
    """)
  }

  def testObject(): Unit = {
    assertDependenciesAre("""
    object O {
      object Foo
      Foo
    }
    """, "O.Foo")
  }

  def testQualifier(): Unit = {
    assertDependenciesAre("""
    object O {
      object Foo {
        object Bar
      }
      Foo.Bar
    }
    """, "O.Foo")
  }

  def testPrimaryConstructor(): Unit = {
    assertDependenciesAre("""
    object O {
      class C
      new C
    }
    """, "O.C")
  }

  def testSecondaryConstructor(): Unit = {
    assertDependenciesAre("""
    object O {
      class C(i: Int, s: String) {
        def this(i: Int) {
          this(i, "")
        }    
      }
      new C(1)
    }
    """, "O.C")
  }

  def testCaseClassCopy(): Unit = {
    assertDependenciesAre("""
    object O {
      case class C(v: Any)
      C(null).copy(v = null)
    }
    """, "O.C")
  }


  def testSyntheticApply(): Unit = {
    assertDependenciesAre("""
    object O {
      case class C()
      C()
    }
    """, "O.C")
  }

  def testSyntheticUnapply(): Unit = {
    assertDependenciesAre("""
    object O {
      case class C()
      null match {
        case C() =>
      }
    }
    """, "O.C")
  }

  def testSyntheticInfixUnapply(): Unit = {
    assertDependenciesAre("""
    object O {
      case class C(a: Any, b: Any)
      null match {
        case _ C _ =>
      }
    }
    """, "O.C")
  }

  def testSyntheticUnapplySeq(): Unit = {
    assertDependenciesAre("""
    object O {
      case class C(seq: Any*)
      null match {
        case C(1, 2, 3) =>
      }
    }
    """, "O.C")
  }

  def testExplicitApply(): Unit = {
    assertDependenciesAre("""
    object O {
      object Foo {
        def apply() = null
      }
      Foo()
    }
    """, "O.Foo")
  }

  def testExplicitUnapply(): Unit = {
    assertDependenciesAre("""
    object O {
      object Foo {
        def unapply() = null
      }
      null match {
        case Foo() =>
      }
    }
    """, "O.Foo")
  }

  def testExplicitInfixUnapply(): Unit = {
    assertDependenciesAre("""
    object O {
      object Foo {
        def unapply(v: Any): Option[(Any, Any)] = null
      }
      null match {
        case _ Foo _ =>
      }
    }
    """, "O.Foo")
  }

  def testExplicitUnapplySeq(): Unit = {
    assertDependenciesAre("""
    object O {
      object Foo {
        def unapplySeq(): Seq[Any] = null
      }
      null match {
        case Foo(1, 2, 3) =>
      }
    }
    """, "O.Foo")
  }

  def testFunction(): Unit = {
    assertDependenciesAre("""
    object O {
      def foo() {}
      foo()
    }
    """, "O.foo")
  }

  def testValue(): Unit = {
    assertDependenciesAre("""
    object O {
      val foo = 1
      foo
    }""", "O.foo")
  }

  def testVariable(): Unit = {
    assertDependenciesAre("""
    object O {
      var foo = 1
      foo
    }""", "O.foo")
  }

  def testNonStaticMembers(): Unit = {
    assertDependenciesAre("""
    object O {
      class A {
        def foo() {}
        var bar = null
        val moo = null
      }
      class B extends A {
        foo()
        bar
        moo
      }
    }
    """, "O.A")
  }

  def testInheritedMemberImport(): Unit = {
    assertDependenciesAre("""
    object O {
      trait A {
        def foo() {}
      }
      object B extends A
      import B._
      foo()
    }""", "O.A", "O.B", "O.B.foo")
  }

  // package
  // implicit conversions
  // import, T
  // injected

  private def assertDependenciesAre(@Language("Scala") code: String, expected: String*): Unit = {
    val file = parseText(code)

    val descriptors = Dependency.collect(file.getTextRange)(file).map {
      case (path, _) => path.asString()
    }.toSet

    Assert.assertEquals(expected.toSet, descriptors)
  }
}