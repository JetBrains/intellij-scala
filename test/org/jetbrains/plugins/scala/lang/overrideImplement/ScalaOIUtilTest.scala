package org.jetbrains.plugins.scala
package lang.overrideImplement

import org.jetbrains.plugins.scala.base.SimpleTestCase
import overrideImplement.ScalaOIUtil
import lang.psi.api.toplevel.typedef.ScTemplateDefinition
import junit.framework.Assert
import org.intellij.lang.annotations.Language

/**
 * Pavel Fatin
 */

class ScalaOIUtilTest extends SimpleTestCase {
  private final val Prefix = "object Holder {\n  "

  private final val Suffix = "\n}"

  // val / var / def / type
  // overloading
  // setter / getter
  // generics
  // _
  // return type

  // holder (trait, class, object, new)

  // implemented as mixin
  // abstract override

  def testEmpty() {
    assertUnimplemented("trait T { }; new T {}")
    assertUnimplemented("abstract case class C; new C {}")
  }

  def testSelf() {
    assertUnimplemented("trait T { }")
    assertUnimplemented("trait T { def f }")
  }

  def testMembers() {
    assertUnimplemented("trait T { def f }; new T {}", "f: Unit")
    assertUnimplemented("trait T { var f }; new T {}", "f: Any")
    assertUnimplemented("trait T { type X }; new T {}", "X")
  }

  def testSources() {
    assertUnimplemented("trait T { def f }; new T {}", "f: Unit")
    assertUnimplemented("abstract class T { def f }; new T {}", "f: Unit")
  }

  def testTargets() {
    assertUnimplemented("trait T { def f }; new T {}", "f: Unit")
    assertUnimplemented("trait T { def f }; class H extends T {}", "f: Unit")
    assertUnimplemented("trait T { def f }; abstract class H extends T {}", "f: Unit")
    assertUnimplemented("trait T { def f }; trait H extends T {}", "f: Unit")
    assertUnimplemented("trait T { def f }; object H extends T {}", "f: Unit")
  }

  private def assertUnimplemented(@Language(value = "Scala", prefix = Prefix, suffix = Suffix) code: String,
                                  names: String*) {
    Assert.assertEquals(names.toList, unimplementedIn(code).toList)
  }

  private def unimplementedIn(@Language(value = "Scala", prefix = Prefix, suffix = Suffix) code: String) = {
    val definition = (Predef + code + Suffix).parse.children.filterByType(classOf[ScTemplateDefinition]).toSeq.last
    val members = ScalaOIUtil.toMembers(ScalaOIUtil.getMembersToImplement(definition))
    members.map(_.getText)
  }
}

