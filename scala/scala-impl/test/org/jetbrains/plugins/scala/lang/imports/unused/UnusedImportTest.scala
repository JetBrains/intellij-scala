package org.jetbrains.plugins.scala.lang.imports.unused

import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class UnusedImportTest_Common_2 extends UnusedImportTestBase with MatcherAssertions {

  def testTwoUnusedSelectorsOnSameLine(): Unit = {
    val text =
      """
        |import java.util.{Set, ArrayList}
        |
        |object Doo
      """.stripMargin
    assertMatches(messages(text)) {
      case HighlightMessage("import java.util.{Set, ArrayList}", _) :: Nil =>
    }
  }

  def testTwoUnusedSelectorsOnSameLine_InWorksheet(): Unit = {
    val text =
      """
        |import java.util.{Set, ArrayList}
        |
        |object Doo
      """.stripMargin
    assertMatches(messages(text, "dummy.sc")) {
      case HighlightMessage("import java.util.{Set, ArrayList}", _) :: Nil =>
    }
  }

  def testUsedImportFromInterpolatedString(): Unit = {
    val text =
      """
        |object theObj {
        |  implicit class StringConversion(val sc: StringContext) {
        |    def zzz(args: Any*): String = {
        |      "blabla"
        |    }
        |  }
        |}
        |
        |class MainTest {
        |  import theObj._
        |  def main(args: Array[String]): Unit = {
        |    val s: String = zzz"blblablas"
        |  }
        |}
      """.stripMargin
    assertMatches(messages(text)) {
      case Nil =>
    }
  }

  def testMethodCallImplicitParameter(): Unit = {
    val text =
      """import scala.concurrent.ExecutionContext
        |import scala.concurrent.ExecutionContext.Implicits.global
        |
        |object Test {
        |  def foo(implicit ec: ExecutionContext): Unit = {}
        |
        |  foo
        |}""".stripMargin
    assertMatches(messages(text)) {
      case Nil =>
    }
  }

  def testSCL9538(): Unit = {
    val text =
      """
        |import scala.concurrent.ExecutionContext
        |import scala.concurrent.ExecutionContext.Implicits.global
        |
        |class AppModel(implicit ec: ExecutionContext) {
        |
        |}
        |
        |val x = new AppModel
      """.stripMargin
    assert(messages(text).isEmpty)
  }

  def testShadowAndWildcard(): Unit = {
    val text =
      """
        |object A {
        |  class X
        |  class Y
        |}
        |
        |import A.{X => _, _}
        |object B {
        |  new Y
        |}
      """.stripMargin
    assert(messages(text).isEmpty)
  }

  def testSelectorAndWildcard(): Unit = {
    val text =
      """
        |object A {
        |  class X
        |  class Y
        |}
        |
        |import A.{X => Z, _}
        |object B {
        |  new Y
        |}
      """.stripMargin

    assertMatches(messages(text)) {
      case HighlightMessage("X => Z", _) :: Nil =>
    }
  }

  def testUnusedImplicitSelectorAndWildcard(): Unit = {
    val text =
      """object A {
        |  class X
        |  class Y
        |
        |  implicit val s: String = ""
        |}
        |
        |import A.{s => implicitString, X => Z, _}
        |object B {
        |  (new Y, new Z)
        |}
      """.stripMargin

    assertMatches(messages(text)) {
      case HighlightMessage("s => implicitString", _) :: Nil =>
    }
  }

  def testUnusedFoundImplicitSelectorAndWildcard(): Unit = {
    val text =
      """object A {
        |  class X
        |  class Y
        |
        |  implicit val s: String = ""
        |}
        |
        |object B {
        |  import A.{s => implicitString, X => Z, _}
        |
        |  def foo(implicit s: String) = s
        |  foo
        |
        |  new Y
        |}
      """.stripMargin

    assertMatches(messages(text)) {
      case HighlightMessage("X => Z", _) :: Nil =>
    }
  }

  def testSelectorAndShadow(): Unit = {
    val text =
      """object A {
        |  class X
        |  class Y
        |
        |  implicit val s: String = ""
        |}
        |
        |import A.{X => Z, s => _}
        |object B {
        |  new Z
        |}
      """.stripMargin

    assertMatches(messages(text)) {
      case Nil =>
    }
  }

  def testUnusedWildcard(): Unit = {
    val text =
      """
        |object A {
        |  class X
        |  class Y
        |
        |  implicit val s: String = ""
        |}
        |
        |import A.{Y, X => Z, s => _, _}
        |object B {
        |  (new Y, new Z)
        |}
      """.stripMargin

    assertMatches(messages(text)) {
      case HighlightMessage("_", _) :: Nil =>
    }
  }

  def testRedundantWildcardImportFromDefaultOrCurrentPackage(): Unit = {
    myFixture.addFileToProject("org/example/definitions.scala",
      """package org.example
        |
        |class A
        |class B
        |class C
        |""".stripMargin
    )
    val text =
      """package org.example
        |
        |import scala._
        |import scala.Predef._
        |import java.lang._
        |import org.example._
        |
        |object O {
        |  def main(args: Array[String]): Unit = {
        |    println(classOf[Range]) // from scala._
        |    println(classOf[AutoCloseable]) // from scala.Predef._
        |    println(classOf[SeqCharSequence]) // from java.lang._
        |    println(classOf[A]) // from org.example._
        |  }
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import scala._", UnusedImportStatement),
        HighlightMessage("import scala.Predef._", UnusedImportStatement),
        HighlightMessage("import java.lang._", UnusedImportStatement),
        HighlightMessage("import org.example._", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testRedundantWildcardAndSingleImportsFromDefaultOrCurrentPackage(): Unit = {
    myFixture.addFileToProject("org/example/definitions.scala",
      """package org.example
        |
        |class A
        |class B
        |class C
        |""".stripMargin
    )
    val text =
      """package org.example
        |
        |import scala.{Range, _}
        |import scala.Predef.{SeqCharSequence, _}
        |import java.lang.{AutoCloseable, _}
        |import org.example.{A, _}
        |
        |object O {
        |  def main(args: Array[String]): Unit = {
        |    println(classOf[Range]) // from scala._
        |    println(classOf[AutoCloseable]) // from scala.Predef._
        |    println(classOf[SeqCharSequence]) // from java.lang._
        |    println(classOf[A]) // from org.example._
        |  }
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import scala.{Range, _}", UnusedImportStatement),
        HighlightMessage("import scala.Predef.{SeqCharSequence, _}", UnusedImportStatement),
        HighlightMessage("import java.lang.{AutoCloseable, _}", UnusedImportStatement),
        HighlightMessage("import org.example.{A, _}", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testRedundantWildcardAndSingleImportsFromDefaultOrCurrentPackage_1(): Unit = {
    myFixture.addFileToProject("org/example/definitions.scala",
      """package org.example
        |
        |class A
        |class B
        |class C
        |""".stripMargin
    )
    val text =
      """package org.example
        |
        |import scala._
        |import scala.Predef._
        |import java.lang._
        |import org.example._
        |
        |import scala.Range
        |import scala.Predef.SeqCharSequence
        |import java.lang.AutoCloseable
        |import org.example.A
        |
        |object O {
        |  def main(args: Array[String]): Unit = {
        |    println(classOf[Range]) // from scala._
        |    println(classOf[AutoCloseable]) // from scala.Predef._
        |    println(classOf[SeqCharSequence]) // from java.lang._
        |    println(classOf[A]) // from org.example._
        |  }
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import scala._", UnusedImportStatement),
        HighlightMessage("import scala.Predef._", UnusedImportStatement),
        HighlightMessage("import java.lang._", UnusedImportStatement),
        HighlightMessage("import org.example._", UnusedImportStatement),

        HighlightMessage("import scala.Range", UnusedImportStatement),
        HighlightMessage("import scala.Predef.SeqCharSequence", UnusedImportStatement),
        HighlightMessage("import java.lang.AutoCloseable", UnusedImportStatement),
        HighlightMessage("import org.example.A", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testRedundantSingleImportFromDefaultOrCurrentPackage(): Unit = {
    myFixture.addFileToProject("org/example/definitions.scala",
      """package org.example
        |
        |class A
        |class B
        |class C
        |""".stripMargin
    )
    val text =
      """package org.example
        |
        |import scala.Range
        |import scala.Predef.SeqCharSequence
        |import java.lang.AutoCloseable
        |import org.example.A
        |
        |object O {
        |  def main(args: Array[String]): Unit = {
        |    println(classOf[Range]) // from scala._
        |    println(classOf[AutoCloseable]) // from scala.Predef._
        |    println(classOf[SeqCharSequence]) // from java.lang._
        |    println(classOf[A]) // from org.example._
        |  }
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import scala.Range", UnusedImportStatement),
        HighlightMessage("import scala.Predef.SeqCharSequence", UnusedImportStatement),
        HighlightMessage("import java.lang.AutoCloseable", UnusedImportStatement),
        HighlightMessage("import org.example.A", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testSingleImportFromDefaultOrCurrentPackage_WithNameClashWithSomeWildcard_LocalImport(): Unit = {
    myFixture.addFileToProject("org/example/definitions.scala",
      """package org.example
        |
        |class A
        |class B
        |class C
        |""".stripMargin
    )
    myFixture.addFileToProject("org/example/other/definitionsWithCollisions.scala",
      """package org.example.other
        |
        |class A
        |class Range
        |class AutoCloseable
        |
        |class OnlyInOther""".stripMargin
    )
    val text =
      """package org.example
        |
        |import org.example.other._
        |
        |object O {
        |
        |  import scala.Range
        |  import java.lang.AutoCloseable
        |  import org.example.A
        |
        |  def main(args: Array[String]): Unit = {
        |    println(classOf[Range].getName) // from scala._
        |    println(classOf[AutoCloseable].getName) // from scala.Predef._
        |    println(classOf[A].getName) // from org.example._
        |  }
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import org.example.other._", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testDoNotTreatRenamingSelectorFromSamePackageAsRedundant(): Unit = {
    myFixture.addFileToProject("com/example1/usage/definitions.scala",
      """package com.example1.usage
        |
        |class S1
        |class S2
        |class S3
        |class S4
        |""".stripMargin
    )
    val text =
      """package com.example1.usage
        |
        |import com.example1.usage.S1
        |import com.example1
        |import com.example1.{usage => usage_RENAMED}
        |import example1.usage.S2
        |import usage_RENAMED.S3
        |import example1.usage.{S4 => S4_Renamed}
        |
        |object Usage {
        |
        |  def main(args: Array[String]): Unit = {
        |    println(classOf[S1])
        |    println(classOf[S2])
        |    println(classOf[S3])
        |    println(classOf[S4_Renamed])
        |  }
        |}""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import com.example1.usage.S1", UnusedImportStatement),
        HighlightMessage("import example1.usage.S2", UnusedImportStatement),
        HighlightMessage("import usage_RENAMED.S3", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testImportFromAvailablePackage_WithNameClashWithAnotherAvailablePackage(): Unit = {
    myFixture.addFileToProject("aaa/MyData.scala",
      """package aaa
        |class MyData
        |""".stripMargin
    )
    myFixture.addFileToProject("aaa/bbb/MyData.scala",
      """package aaa.bbb
        |class MyData
        |""".stripMargin
    )
    myFixture.addFileToProject("aaa/bbb/ccc/MyData.scala",
      """package aaa.bbb.ccc
        |class MyData
        |""".stripMargin
    )
    myFixture.addFileToProject("aaa/bbb/ccc/ddd/MyData.scala",
      """package aaa.bbb.ccc.ddd
        |class MyData
        |""".stripMargin
    )
    val text =
      """package aaa
        |package bbb
        |package ccc
        |package ddd
        |
        |object Main {
        |  def main(args: Array[String]): Unit = {
        |    //single import
        |    {
        |      import aaa.MyData
        |      println(classOf[MyData])
        |    }
        |
        |    {
        |      import aaa.bbb.MyData
        |      println(classOf[MyData])
        |    }
        |
        |    {
        |      import aaa.bbb.ccc.MyData
        |      println(classOf[MyData])
        |    }
        |
        |    {
        |      import aaa.bbb.ccc.ddd.MyData
        |      println(classOf[MyData])
        |    }
        |
        |    //wildcard import
        |    {
        |      import aaa._
        |      println(classOf[MyData])
        |    }
        |
        |    {
        |      import aaa.bbb._
        |      println(classOf[MyData])
        |    }
        |
        |    {
        |      import aaa.bbb.ccc._
        |      println(classOf[MyData])
        |    }
        |
        |    {
        |      import aaa.bbb.ccc.ddd._
        |      println(classOf[MyData])
        |    }
        |  }
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import aaa.bbb.ccc.ddd.MyData", UnusedImportStatement),
        HighlightMessage("import aaa.bbb.ccc.ddd._", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testImportFromAvailablePackage_WithNameClashWithAnotherAvailablePackage_ScalaLibraryExample(): Unit = {
    val text =
      """package scala
        |package collection
        |package mutable
        |
        |object Main {
        |  //Traversable is already available in `scala.collection.mutable.Traversable`
        |  // (which is in available package)
        |  //So this import can't be removed,
        |  //otherwise scala.collection.mutable.Traversable will be used
        |  import scala.collection.Traversable
        |
        |  def main(args: Array[String]): Unit = {
        |    println(classOf[Traversable[_]])
        |  }
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq[HighlightMessage](),
      messages(text)
    )
  }
}

class UnusedImportTest_212 extends UnusedImportTest_Common_2 {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  def testSingleImportFromDefaultOrCurrentPackage_WithNameClashWithSomeWildcard(): Unit = {
    myFixture.addFileToProject("org/example/definitions.scala",
      """package org.example
        |
        |class A
        |class B
        |class C
        |""".stripMargin
    )
    myFixture.addFileToProject("org/example/other/definitionsWithCollisions.scala",
      """package org.example.other
        |
        |class A
        |class Range
        |class AutoCloseable
        |
        |class OnlyInOther""".stripMargin
    )
    val text =
      """package org.example
        |
        |import scala.Range
        |import java.lang.AutoCloseable
        |import org.example.A
        |
        |import org.example.other._
        |
        |object O {
        |  def main(args: Array[String]): Unit = {
        |    println(classOf[Range].getName) // from scala._
        |    println(classOf[AutoCloseable].getName) // from scala.Predef._
        |    println(classOf[A].getName) // from org.example._
        |  }
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import org.example.A", UnusedImportStatement),
        HighlightMessage("import org.example.other._", UnusedImportStatement),
      ),
      messages(text)
    )
  }
}

class UnusedImportTest_213 extends UnusedImportTest_Common_2 {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  def testSingleImportFromDefaultOrCurrentPackage_WithNameClashWithSomeWildcard(): Unit = {
    myFixture.addFileToProject("org/example/definitions.scala",
      """package org.example
        |
        |class A
        |class B
        |class C
        |""".stripMargin
    )
    myFixture.addFileToProject("org/example/other/definitionsWithCollisions.scala",
      """package org.example.other
        |
        |class A
        |class Range
        |class AutoCloseable
        |
        |class OnlyInOther""".stripMargin
    )
    val text =
      """package org.example
        |
        |import scala.Range
        |import java.lang.AutoCloseable
        |import org.example.A
        |
        |import org.example.other._
        |
        |object O {
        |  def main(args: Array[String]): Unit = {
        |    println(classOf[Range].getName) // from scala._
        |    println(classOf[AutoCloseable].getName) // from scala.Predef._
        |    println(classOf[A].getName) // from org.example._
        |  }
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import org.example.other._", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  /** Same as [[UnusedImportTest_213_XSource3.testShadowAndWildcard_XSource3Syntax]] but without `-Xsource:3 compiler option` */
  def testUnusedAliasImport_WithStarName_WithoutXSource3CompilerOption(): Unit = {
    val text =
      """object A {
        |  class X
        |  class Y
        |}
        |
        |import A.{X => *, _}
        |object B {
        |  new Y
        |}
        |""".stripMargin
    assertCollectionEquals(
      Seq(
        HighlightMessage("X => *", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testUsedAliasImport_WithStarName_WithoutXSource3CompilerOption(): Unit = {
    val text =
      """object A {
        |  class X
        |  class Y
        |}
        |
        |import A.{X => *, _}
        |object B {
        |  new Y
        |  new *
        |}
        |""".stripMargin
    assertCollectionEquals(
      Seq[HighlightMessage](),
      messages(text)
    )
  }
}

class UnusedImportTest_213_XSource3 extends UnusedImportTest_Common_2 {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  override protected def additionalCompilerOptions: Seq[String] = Seq("-Xsource:3")

  def testShadowAndWildcard_XSource3Syntax(): Unit = {
    val text =
      """object A {
        |  class X
        |  class Y
        |  class Z
        |}
        |
        |import A.{X as *, Z => *, *}
        |object B {
        |  new Y
        |}
        |""".stripMargin
    assert(messages(text).isEmpty)
  }

  def testGivenWildcardImport_used(): Unit = {
    val text =
      """
        |object A {
        |  implicit val i: Int = 1
        |}
        |
        |object B {
        |  import A.{given, *}
        |
        |  implicitly[Int]
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq.empty[HighlightMessage],
      messages(text)
    )
  }

  def testGivenWildcardImport_unused(): Unit = {
    val text =
      """
        |object A {
        |  implicit val i: Int = 1
        |}
        |
        |object B {
        |  import A.{given, *}
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import A.{given, *}", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testGivenWildcardImport_unused_but_wildcard_used(): Unit = {
    val text =
      """
        |object A {
        |  def fun(): Unit = ()
        |  implicit val i: Int = 1
        |}
        |
        |object B {
        |  import A.{given, *} // given should be marked as unused, but it's technically too difficult to implement
        |
        |  fun()
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq.empty[HighlightMessage],
      messages(text)
    )
  }
}

final class UnusedImportTest_3 extends UnusedImportTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def testUnusedGivenWildcard_noGivensOrImplicitsUsed(): Unit = {
    val text =
      """package org.example
        |
        |object Usages:
        |  import Declarations.{*, given}
        |  b
        |
        |object Declarations:
        |  given String = "foo"
        |  implicit val i: Int = 2
        |  val b: Boolean = true
        |
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("given", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testUnusedGivenWildcard_implicitImportedWithStarWildcard(): Unit = {
    val text =
      """package org.example
        |
        |object Usages:
        |  import Declarations.{*, given}
        |  summon[Int]
        |
        |object Declarations:
        |  given String = "foo"
        |  implicit val i: Int = 2
        |
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("given", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testUnusedStarWildcard_onlyGivenIsUsedSeparate(): Unit = {
    val text =
      """package org.example
        |
        |object Usages:
        |  import Declarations.*
        |  import Declarations.given
        |  summon[String]
        |
        |object Declarations:
        |  given String = "foo"
        |  implicit val i: Int = 2
        |
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import Declarations.*", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testUnusedStarWildcard_importedByGiven(): Unit = {
    val text =
      """package org.example
        |
        |object Usages:
        |  import Declarations.*
        |  import Declarations.given
        |  summon[String]
        |  println(i)
        |
        |object Declarations:
        |  given String = "foo"
        |  implicit val i: Int = 2
        |
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import Declarations.*", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testUnusedStarWildcard_importedByGiven2(): Unit = {
    val text =
      """package org.example
        |
        |object Usages:
        |  import Declarations.given
        |  import Declarations.*
        |  summon[String]
        |  println(i)
        |
        |object Declarations:
        |  given String = "foo"
        |  implicit val i: Int = 2
        |
        |""".stripMargin

    assertCollectionEquals(
      Seq(
        HighlightMessage("import Declarations.*", UnusedImportStatement),
      ),
      messages(text)
    )
  }

  def testNoHighlightings_givenIsImportedByNameAndImplicitIsWithGivenWildcard(): Unit = {
    val text =
      """package org.example
        |
        |object Usages:
        |  import Declarations.{given_String, given}
        |  summon[Int]
        |  val s = given_String
        |
        |object Declarations:
        |  given String = "foo"
        |  implicit val i: Int = 2
        |
        |""".stripMargin

    assertCollectionEquals(
      Seq[HighlightMessage](),
      messages(text)
    )
  }

  def testNoHighlightings_implicitIsImportedWithGivenType(): Unit = {
    val text =
      """package org.example
        |
        |object Usages:
        |  import Declarations.given Int
        |  summon[Int]
        |
        |object Declarations:
        |  implicit val i: Int = 2
        |
        |""".stripMargin

    assertCollectionEquals(
      Seq[HighlightMessage](),
      messages(text)
    )
  }

  def testSCL22596(): Unit = {
    val text =
      """
        |package org.example
        |
        |import some_package.{derived, FooWithExtensionDerive, Foo}
        |
        |case class Bar1() derives Foo
        |case class Bar2() derives FooWithExtensionDerive
        |
        |package some_package {
        |  trait Foo[A]
        |  object Foo {
        |    def derived[A]: Foo[A] = ???
        |  }
        |
        |  trait FooWithExtensionDerive[A]
        |  object FooWithExtensionDerive
        |  extension (foo: FooWithExtensionDerive.type)
        |    def derived[A]: FooWithExtensionDerive[A] = ???
        |}
        |""".stripMargin

    assertCollectionEquals(
      Seq[HighlightMessage](),
      messages(text)
    )
  }
}

// TODO(SCL-21608):
//  There is some divergence between Scala 3 compiler's and IntelliJ Scala Plugin's logic in marking wildcard imports unused.
//  These tests are supposed to fail until the logic is fixed
final class UnusedImportTest_3_Failing extends UnusedImportTestBase {
  private def shouldHaveMessages = false

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def doTest(text: String, expectedMessages: HighlightMessage*): Unit =
    assertCollectionEquals(
      if (shouldHaveMessages) expectedMessages else Seq.empty,
      messages(text)
    )

  def testUnusedStarWildcard_onlyGivenIsUsed(): Unit = {
    val text =
      """package org.example
        |
        |object Usages:
        |  import Declarations.{*, given}
        |  summon[String]
        |
        |object Declarations:
        |  given String = "foo"
        |  implicit val i: Int = 2
        |
        |""".stripMargin

    doTest(text, HighlightMessage("*", UnusedImportStatement))
  }

  def testUnusedStarWildcard_onlyGivenIsUsedSwapped(): Unit = {
    val text =
      """package org.example
        |
        |object Usages:
        |  import Declarations.{given, *}
        |  summon[String]
        |
        |object Declarations:
        |  given String = "foo"
        |  implicit val i: Int = 2
        |
        |""".stripMargin

    doTest(text, HighlightMessage("*", UnusedImportStatement))
  }
}
