package org.jetbrains.plugins.scala
package lang.overrideImplement

import org.jetbrains.plugins.scala.base.SimpleTestCase
import overrideImplement.ScalaOIUtil
import junit.framework.Assert
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions._
import lang.psi.api.ScalaFile
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTemplateDefinition}

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

  def testSOE() {
    assertUnimplemented("trait A; trait B extends D; " +
      "trait C extends A with B; trait D extends B with C;" +
      "object X extends D {}")
  }

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
    //todo: important: in script file resolve is ok. In any other file problems with resolve to T,
    //todo: because of wrong package structure.
    assertUnimplemented("trait T { def f }; new T {}", "f: Unit")
    assertUnimplemented("1; trait T { def f }; class H extends T {}", "f: Unit")
    assertUnimplemented("1; trait T { def f }; abstract class H extends T {}", "f: Unit")
    assertUnimplemented("1; trait T { def f }; trait H extends T {}", "f: Unit")
    assertUnimplemented("1; trait T { def f }; object H extends T {}", "f: Unit")
  }

  private def assertUnimplemented(@Language(value = "Scala", prefix = Prefix, suffix = Suffix) code: String,
                                  names: String*) {
    Assert.assertEquals(names.toList, unimplementedIn(code).toList)
  }

  private def unimplementedIn(@Language(value = "Scala", prefix = Prefix, suffix = Suffix) code: String) = {
    val text: String = "" + code + Suffix
    val file: ScalaFile = text.parse
    val templateDefinitions: Seq[ScTemplateDefinition] = file.children.filterByType(classOf[ScTemplateDefinition]).toSeq
    val lastDefinition: ScTemplateDefinition = templateDefinitions.last
    val members = ScalaOIUtil.toMembers(ScalaOIUtil.getMembersToImplement(lastDefinition))
    members.map(_.getText)
  }
}

