package org.jetbrains.plugins.scala
package lang
package dependency

import org.intellij.lang.annotations.Language
import org.junit.Assert

/**
 * Pavel Fatin
 */

class DependencyTest extends base.SimpleTestCase {
  def testClass() {
    assertDependenciesAre("""
    object O {
      class C
      classOf[C]
    }
    """, "O.C")
  }

  def testSyntheticClass() {
    assertDependenciesAre("""
    object O {
      classOf[Int]
    }
    """)
  }

  def testObject() {
    assertDependenciesAre("""
    object O {
      object Foo
      Foo
    }
    """, "O.Foo")
  }

  def testQualifier() {
    assertDependenciesAre("""
    object O {
      object Foo {
        object Bar
      }
      Foo.Bar
    }
    """, "O.Foo")
  }

  def testPrimaryConstructor() {
    assertDependenciesAre("""
    object O {
      class C
      new C
    }
    """, "O.C")
  }

  def testSecondaryConstructor() {
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

  def testCaseClassCopy() {
    assertDependenciesAre("""
    object O {
      case class C(v: Any)
      C(null).copy(v = null)
    }
    """, "O.C")
  }


  def testSyntheticApply() {
    assertDependenciesAre("""
    object O {
      case class C()
      C()
    }
    """, "O.C")
  }

  def testSyntheticUnapply() {
    assertDependenciesAre("""
    object O {
      case class C()
      null match {
        case C() =>
      }
    }
    """, "O.C")
  }

  def testSyntheticInfixUnapply() {
    assertDependenciesAre("""
    object O {
      case class C(a: Any, b: Any)
      null match {
        case _ C _ =>
      }
    }
    """, "O.C")
  }

  def testSyntheticUnapplySeq() {
    assertDependenciesAre("""
    object O {
      case class C(seq: Any*)
      null match {
        case C(1, 2, 3) =>
      }
    }
    """, "O.C")
  }

  def testExplicitApply() {
    assertDependenciesAre("""
    object O {
      object Foo {
        def apply() = null
      }
      Foo()
    }
    """, "O.Foo")
  }

  def testExplicitUnapply() {
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

  def testExplicitInfixUnapply() {
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

  def testExplicitUnapplySeq() {
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

  def testFunction() {
    assertDependenciesAre("""
    object O {
      def foo() {}
      foo()
    }
    """, "O.foo")
  }

  def testValue() {
    assertDependenciesAre("""
    object O {
      val foo = 1
      foo
    }""", "O.foo")
  }

  def testVariable() {
    assertDependenciesAre("""
    object O {
      var foo = 1
      foo
    }""", "O.foo")
  }

  def testNonStaticMembers() {
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

  def testInheritedMemberImport() {
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

  private def assertDependenciesAre(@Language("Scala") code: String, expected: String*) {
    val file = parseText(code)

    val descriptors = refactoring.Associations
      .collectAssociationsForRange(file.getTextRange)(file)
      .map(_.path.asString())
      .toSet

    Assert.assertEquals(expected.toSet, descriptors)
  }
}