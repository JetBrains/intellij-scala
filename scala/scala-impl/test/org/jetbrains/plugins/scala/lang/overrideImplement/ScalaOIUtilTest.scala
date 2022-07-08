package org.jetbrains.plugins.scala
package lang.overrideImplement

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import org.junit.Assert

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

  def testSOE(): Unit = {
    assertUnimplemented("trait A; trait B extends D; " +
      "trait C extends A with B; trait D extends B with C;" +
      "object X extends D {}")
  }

  def testEmpty(): Unit = {
    assertUnimplemented("trait T { }; new T {}")
    assertUnimplemented("abstract case class C; new C {}")
  }

  def testSelf(): Unit = {
    assertUnimplemented("trait T { }")
    assertUnimplemented("trait T { def f }")
  }

  def testConvertedName(): Unit = {
    assertUnimplemented(
      """
        |1
        |abstract class PP {
        |  def !! : Int
        |}
        |class T extends PP {
        |  def $bang$bang: Int = 0
        |}
      """.replace("\r", "").stripMargin
    )
  }

  def testOverAbstract(): Unit = {
    assertUnimplemented(
      """
        |1
        |trait A {
        |  def foo: Int
        |}
        |trait B {
        |  def foo: Int = 1
        |}
        |class T extends B with A {
        |}
      """.replace("\r", "").stripMargin
    )
  }

  def testMembers(): Unit = {
    assertUnimplemented("trait T { def f }; new T {}", "f: Unit")
    assertUnimplemented("trait T { var f }; new T {}", "f: Any")
    assertUnimplemented("trait T { type X }; new T {}", "X")
  }

  def testSources(): Unit = {
    assertUnimplemented("trait T { def f }; new T {}", "f: Unit")
    assertUnimplemented("abstract class T { def f }; new T {}", "f: Unit")
  }

  def testTargets(): Unit = {
    //todo: important: in script file resolve is ok. In any other file problems with resolve to T,
    //todo: because of wrong package structure.
    assertUnimplemented("trait T { def f }; new T {}", "f: Unit")
    assertUnimplemented("1; trait T { def f }; class H extends T {}", "f: Unit")
    assertUnimplemented("1; trait T { def f }; abstract class H extends T {}", "f: Unit")
    assertUnimplemented("1; trait T { def f }; trait H extends T {}", "f: Unit")
    assertUnimplemented("1; trait T { def f }; object H extends T {}", "f: Unit")
  }

  private def assertUnimplemented(@Language(value = "Scala", prefix = Prefix, suffix = Suffix) code: String,
                                  names: String*): Unit = {
    Assert.assertEquals(names.toList, unimplementedIn(code).toList)
  }

  private def unimplementedIn(@Language(value = "Scala", prefix = Prefix, suffix = Suffix) code: String) = {
    val text: String = "" + code + Suffix
    val file: ScalaFile = text.parse
    val templateDefinitions: Seq[ScTemplateDefinition] = file.children.filterByType[ScTemplateDefinition].toSeq
    val lastDefinition: ScTemplateDefinition = templateDefinitions.last
    val members = ScalaOIUtil.getMembersToImplement(lastDefinition)
    members.map(_.getText)
  }
}

