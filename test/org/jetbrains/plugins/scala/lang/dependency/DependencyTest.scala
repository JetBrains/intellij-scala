package org.jetbrains.plugins.scala
package lang.dependency

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.junit.Assert

/**
 * Pavel Fatin
 */

class DependencyTest extends SimpleTestCase {
  def testClass() {
    assertDependenciesAre("""
    object O {
      class C
      classOf[C]
    }
    """, ("C", "ScClass: C", "O.C"))
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
    """, ("Foo", "ScObject: Foo", "O.Foo"))
  }
  
  def testQualifier() {
    assertDependenciesAre("""
    object O {
      object Foo
    }
    O.Foo
    """, ("O", "ScObject: O", "O"))
  }

  def testPrimaryConstructor() {
    assertDependenciesAre("""
    object O {
      class C
      new C
    }
    """, ("C", "PrimaryConstructor", "O.C"))
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
    """, ("C", "ScFunctionDefinition: this", "O.C"))
  }

  def testCaseClassCopy() {
    assertDependenciesAre("""
    object O {
      case class C(v: Any)
      C(null).copy(v = null)
    }
    """, ("C", "ScFunctionDefinition: apply", "O.C"))
  }


  def testSyntheticApply() {
    assertDependenciesAre("""
    object O {
      case class C()
      C()
    }
    """, ("C", "ScFunctionDefinition: apply", "O.C"))
  }

  def testSyntheticUnapply() {
    assertDependenciesAre("""
    object O {
      case class C()
      null match {
        case C() =>
      }
    }
    """, ("C", "ScFunctionDefinition: unapply", "O.C"))
  }

  def testSyntheticInfixUnapply() {
    assertDependenciesAre("""
    object O {
      case class C(a: Any, b: Any)
      null match {
        case _ C _ =>
      }
    }
    """, ("C", "ScFunctionDefinition: unapply", "O.C"))
  }

  def testSyntheticUnapplySeq() {
    assertDependenciesAre("""
    object O {
      case class C(seq: Any*)
      null match {
        case C(1, 2, 3) =>
      }
    }
    """, ("C", "ScFunctionDefinition: unapplySeq", "O.C"))
  }

  def testExplicitApply() {
    assertDependenciesAre("""
    object O {
      object Foo {
        def apply() = null
      }
      Foo()
    }
    """, ("Foo", "ScFunctionDefinition: apply", "O.Foo"))
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
    """, ("Foo", "ScFunctionDefinition: unapply", "O.Foo"))
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
    """, ("Foo", "ScFunctionDefinition: unapply", "O.Foo"))
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
    """, ("Foo", "ScFunctionDefinition: unapplySeq", "O.Foo"))
  }

  def testFunction() {
    assertDependenciesAre("""
    object O {
      def foo() {}
      foo()
    }
    """, ("foo", "ScFunctionDefinition: foo", "O.foo"))
  }

  def testValue() {
    assertDependenciesAre("""
    object O {
      val foo = 1
      foo
    }""", ("foo", "ReferencePattern: foo", "O.foo"))
  }

  def testVariable() {
    assertDependenciesAre("""
    object O {
      var foo = 1
      foo
    }""", ("foo", "ReferencePattern: foo", "O.foo"))
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
    """, ("A", "PrimaryConstructor", "O.A"))
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
    }""", ("A", "ScTrait: A", "O.A"), ("B", "ScObject: B", "O.B"), ("foo", "ScFunctionDefinition: foo", "O.B.foo"))
  }

  // package
  // implicit conversions
  // import, T
  // injected

  private def assertDependenciesAre(@Language("Scala") code: String, expectations: (String, String, String)*) {
    val file = parseText(code)

    val descriptors = Dependency.dependenciesIn(file)
            .map(it => (it.source.getText, it.target.toString, it.path.asString))

    Assert.assertEquals(expectations.toList, descriptors.toList)
  }
}