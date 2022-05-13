package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.{REFSRC, REFTGT}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

abstract class ResolvePrecedenceTest extends SimpleResolveTestBase {

  def testSCL6146(): Unit = doResolveTest(
    "case class Foo()" -> "Foo.scala",

    s"""
       |object SCL6146 {
       |  case class ${REFTGT}Foo()
       |}
       |class SCL6146 {
       |  import SCL6146._
       |  def foo(c: Any) = c match {
       |    case f: ${REFSRC}Foo =>
       |  }
       |
       |  def foo2 = Foo()
       |}
       |""".stripMargin -> "SCL6146.scala"
  )

  //SCL-16538
  def testSCL16538(): Unit = {
    doResolveTest(
      s"""
         |package outerP
         |
         |class OuterObj {
         |  def test(): Unit = ()
         |}
         |
         |object OuterObj {
         |  def ${REFTGT}instance: OuterObj = ???
         |}
         |""".stripMargin -> "OuterObj.scala"
      ,
      s"""
         |package outerP
         |package innerP
         |
         |import OuterObj.{instance => OuterObj} // <- this import does not shadow outerP.OuterObj and is therefor shown as unused
         |
         |object InnerObj {
         |  ${REFSRC}OuterObj.test() // this will fail because intellij thinks OuterObj refers to outerP.OuterObj
         |}
         |
         |""".stripMargin -> "InnerObj.scala")
  }

  def testImportShadowsOuterPackage(): Unit = {
    doResolveTest(
      """package outer
        |
        |object Clash
        |""".stripMargin -> "OuterClash.scala",

      s"""package other
         |
         |object ${REFTGT}Clash""".stripMargin -> "OtherClash.scala",

      s"""package outer
         |package inner
         |
         |import other.Clash
         |
         |object InnerObj {
         |  ${REFSRC}Clash
         |}
         |""".stripMargin -> "InnerObj.scala")
  }

  def testInheritedObject(): Unit = {
    doResolveTest(
      s"""
         |package shapeless
         |
         |trait Poly1 {
         |  type Case[A] = Nothing
         |  object ${REFTGT}Case
         |}
         |""".stripMargin -> "Poly1.scala",

      """
        |package shapeless
        |
        |object PolyDefns {
        |  abstract class Case
        |  object Case
        |}
        |""".stripMargin -> "PolyDefns.scala",

      """
        |package object shapeless {
        |  val poly = PolyDefns
        |}
        |""".stripMargin -> "package.scala",

      s"""
         |package test
         |
         |import shapeless._
         |import shapeless.poly._
         |
         |object Exractor extends Poly1 {
         |  val c = ${REFSRC}Case
         |}
         |""".stripMargin -> "Extractor.scala"
    )
  }

  def testClassNameFromSingleImportFromAvailablePackageClashesWithNameFromAnotherAvailablePackage_ScalaCollectionExample(): Unit = {
    doResolveTest(
      myFixture.getJavaFacade.findClass("scala.collection.Traversable"),
      s"""package scala
         |package collection
         |package mutable
         |
         |object Main {
         |  //Traversable is already available in `scala.collection.mutable.Traversable`
         |  // (which is in available package)
         |  //So this import can't be removed,
         |  //otherwise scala.collection.mutable.Traversable will be used
         |
         |  import scala.collection.Traversable
         |
         |  def main(args: Array[String]): Unit = {
         |    println(classOf[${REFSRC}Traversable[_]])
         |  }
         |}
         |""".stripMargin -> "Main.scala"
    )
  }

  def testClassNameFromSingleImportFromAvailablePackageClashesWithNameFromAnotherAvailablePackage(): Unit = {
    val commonDefs =
      """class MyClassTop1
        |class MyClassTop2[T]
        |""".stripMargin
    val commonDefsPackageObject =
      """class MyClassInPackageObject1
        |class MyClassInPackageObject2[T]
        |type MyAliasInPackageObject1 = String
        |type MyAliasInPackageObject2[T] = Seq[T]
        |val myValInPackageObject = 0
        |def myDefInPackageObject = 0
        |""".stripMargin

    myFixture.addFileToProject("aaa/package.scala",
      s"""$commonDefsPackageObject
         |""".stripMargin
    )
    myFixture.addFileToProject("aaa/defs.scala",
      s"""package aaa
         |$commonDefs
         |""".stripMargin
    )

    myFixture.addFileToProject("aaa/bbb/package.scala",
      s"""package aaa
         |
         |package object bbb {
         |$commonDefsPackageObject
         |}
         |""".stripMargin
    )
    myFixture.addFileToProject("aaa/bbb/defs.scala",
      s"""package aaa.bbb
         |$commonDefs
         |""".stripMargin
    )

    myFixture.addFileToProject("aaa/bbb/ccc/package.scala",
      s"""package aaa.bbb
         |
         |package object ccc {
         |$commonDefsPackageObject
         |}
         |""".stripMargin
    )
    myFixture.addFileToProject("aaa/bbb/ccc/defs.scala",
      s"""package aaa.bbb.ccc
         |$commonDefs
         |""".stripMargin
    )

    myFixture.addFileToProject("aaa/bbb/ccc/ddd/package.scala",
      s"""package aaa.bbb.ccc
         |
         |package object ddd {
         |$commonDefsPackageObject
         |}
         |""".stripMargin
    )
    myFixture.addFileToProject("aaa/bbb/ccc/ddd/defs.scala",
      s"""package aaa.bbb.ccc.ddd
         |$commonDefs
         |""".stripMargin
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyClassInPackageObject1"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.MyClassInPackageObject1
         |    val d1: ${REFSRC}MyClassInPackageObject1 = null
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyClassInPackageObject2"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.MyClassInPackageObject2
         |    val d2: ${REFSRC}MyClassInPackageObject2[_] = null
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyClassTop1"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.MyClassTop1
         |    val dt1: ${REFSRC}MyClassTop1 = null
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyClassTop2"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.MyClassTop2
         |    val dt2: ${REFSRC}MyClassTop2[_] = null
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyAliasInPackageObject1"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.MyAliasInPackageObject1
         |    val a1: ${REFSRC}MyAliasInPackageObject1 = null
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyAliasInPackageObject2"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.MyAliasInPackageObject2
         |    val a2: ${REFSRC}MyAliasInPackageObject2[_] = null
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyAliasInPackageObject2"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.myValInPackageObject
         |    ${REFSRC}myValInPackageObject
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )

    doResolveTest(
      myFixture.getJavaFacade.findClass("aaa.bbb.MyAliasInPackageObject2"),
      s"""package aaa
         |package bbb
         |package ccc
         |package ddd
         |
         |object Main {
         |  def main(args: Array[String]): Unit = {
         |    import aaa.bbb.myDefInPackageObject
         |    ${REFSRC}myDefInPackageObject
         |  }
         |}
         |
         |""".stripMargin -> "Main.scala"
    )
  }
}

class ResolvePrecedenceTest2_13 extends ResolvePrecedenceTest {

  override protected def supportedIn(version: ScalaVersion) = version >= LatestScalaVersions.Scala_2_13

  def testSCL16057(): Unit = doResolveTest(
    s"""
       |package foo
       |
       |import cats.Monoid
       |object Test {
       |  val a: Mon${REFSRC}oid[Int] = ???
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package object cats {
       |  type Mo${REFTGT}noid[A] = A
       |}
       |""".stripMargin -> "DefinesRef.scala",
    s"""
       |package foo
       |trait Monoid[A]
       |""".stripMargin -> "PackageLocal.scala"
  )

  def testSCL16057_nonTopImport(): Unit = doResolveTest(
    s"""
       |package foo
       |
       |object Test {
       |  import cats.Monoid
       |
       |  val a: Mon${REFSRC}oid[Int] = ???
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package object cats {
       |  type ${REFTGT}Monoid[A] = A
       |}
       |""".stripMargin -> "DefinesRef.scala",
    s"""
       |package foo
       |trait Monoid[A]
       |""".stripMargin -> "PackageLocal.scala"
  )

  //ScalacIssue11593
  def testWildcardImportSameUnit(): Unit = doResolveTest(
    s"""
       |package foo {
       |  class ${REFTGT}Properties
       |
       |  import java.util._
       |
       |  object X extends App {
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin
  )

  //ScalacIssue11593
  def testWildcardImportOtherUnit(): Unit = doResolveTest(myFixture.getJavaFacade.findClass("java.util.Properties"),
    s"""
       |package foo {
       |  import java.util._
       |
       |  object X extends App {
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package foo
       |
       |class Properties
       |
       |""".stripMargin -> "DefinesRef.scala"
  )

  //SCL-16305
  def testClassObjectNameClash(): Unit = testNoResolve(
    """
      |package hints
      |
      |case class Hint()
      |object Hint
      |""".stripMargin -> "hintsHint.scala",

    """
      |package implicits
      |
      |import hints.Hint
      |
      |object Hint {
      |  def addTo(hint: Hint) = ???
      |}
      |
      |""".stripMargin -> "implicitsHint.scala",

    s"""
       |import hints.Hint
       |
       |package object implicits {
       |  def add(hint: Hint) = ${REFSRC}Hint.addTo(hint) //ambiguous reference to object Hint
       |}
       |""".stripMargin -> "package.scala"
  )

  //resolves to imported object
  def testDefaultPackage(): Unit = {
    doResolveTest(
      s"""
         |object Resolvers {
         |  val sonatypeSnaps = ???
         |}
         |""".stripMargin -> "Resolvers.scala",

      s"""
         |package sbt
         |
         |object Resolvers {
         |  val ${REFTGT}sonatypeSnaps = ???
         |}""".stripMargin -> "sbtResolvers.scala",

      s"""import sbt._
         |
         |class Test {
         |  import Resolvers._
         |
         |  ${REFSRC}sonatypeSnaps
         |}
         |""".stripMargin -> "Test.scala"
    )
  }

  //SCL-16562
  def testClassName_FromWildcardImport_ClashesWith_NotExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      myFixture.getJavaFacade.findClass("scala.util.Random"),
      s"""package org.example.data
         |
         |class Random //also exists in scala.util
         |""".stripMargin -> "Random.scala",
      s"""package org.example.data
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Random = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_FromWildcardImport_ClashesWith_ExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Random //also exists in scala.util
         |""".stripMargin -> "Random.scala",
      s"""package org.example.data
         |
         |import org.example.data.Random
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Random = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_FromWildcardImport_ClashesWith_ExplicitlyImportedClass_FromOtherPackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Random //also exists in scala.util
         |""".stripMargin -> "Random.scala",
      s"""package org.example.usage
         |
         |import org.example.data.Random
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Random = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_DefaultPackage_ClashesWith_NotExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      myFixture.getJavaFacade.findClass("scala.lang.Responder"),
      s"""package org.example.data
         |
         |class ${REFTGT}Responder //also exists in scala.lang (default package)
         |""".stripMargin -> "Responder.scala",
      s"""package org.example.data
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Responder = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_DefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Responder //also exists in scala.lang (default package)
         |""".stripMargin -> "Responder.scala",
      s"""package org.example.data
         |
         |import org.example.data.Responder
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Responder = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_DefaultPackage_ClashesWith_ExplicitlyImportedClass_FromOtherPackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Responder //also exists in scala.lang (default package)
         |""".stripMargin -> "Responder.scala",
      s"""package org.example.usage
         |
         |import org.example.data.Responder
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Responder = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }
}

class ResolvePrecedenceTest2_12 extends ResolvePrecedenceTest {

  override protected def supportedIn(version: ScalaVersion) = version <= LatestScalaVersions.Scala_2_12

  def testSCL16057(): Unit = doResolveTest(
    s"""
       |package foo
       |
       |import cats.Monoid
       |object Test {
       |  val a: Mon${REFSRC}oid[Int] = ???
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package object cats {
       |  type Monoid[A] = A
       |}
       |""".stripMargin -> "DefinesRef.scala",
    s"""
       |package foo
       |trait M${REFTGT}onoid[A]
       |""".stripMargin -> "PackageLocal.scala"
  )

  def testSCL16057_nonTopImport(): Unit = doResolveTest(
    s"""
       |package foo
       |
       |object Test {
       |  import cats.Monoid
       |
       |  val a: Mon${REFSRC}oid[Int] = ???
       |}
       |""".stripMargin -> "HasRef.scala",
    s"""
       |package object cats {
       |  type ${REFTGT}Monoid[A] = A
       |}
       |""".stripMargin -> "DefinesRef.scala",
    s"""
       |package foo
       |trait Monoid[A]
       |""".stripMargin -> "PackageLocal.scala"
  )

  //ScalacIssue11593
  def testWildcardImportSameUnit_topLevelImport(): Unit = doResolveTest(
    s"""
       |package foo {
       |  class ${REFTGT}Properties
       |
       |  import java.util._
       |
       |  object X extends App {
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin
  )

  def testWildcardImportSameUnit_nonTopImport(): Unit = testNoResolve(
    s"""
       |package foo {
       |  class Properties
       |
       |  object X extends App {
       |    import java.util._
       |
       |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
       |    bar(new Properties)
       |  }
       |}
       |""".stripMargin -> "HasAmbiguity.scala"
  )

  def testWildcardImportOtherUnit_topLevelImport(): Unit =
    doResolveTest(
      s"""
         |package foo {
         |  import java.util._
         |
         |  object X extends App {
         |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
         |    bar(new Properties)
         |  }
         |}
         |""".stripMargin -> "HasRef.scala",
      s"""
         |package foo
         |
         |class ${REFTGT}Properties
         |
         |""".stripMargin -> "DefinesRef.scala"
    )

  def testWildcardImportOtherUnit_nonTopImport(): Unit =
    doResolveTest(myFixture.getJavaFacade.findClass("java.util.Properties"),
      s"""
         |package foo {
         |  object X extends App {
         |    import java.util._
         |
         |    def bar(x: P${REFSRC}roperties): Unit = println(x.getClass.getName)
         |    bar(new Properties)
         |  }
         |}
         |""".stripMargin -> "HasRef.scala",
      s"""
         |package foo
         |
         |class Properties
         |
         |""".stripMargin -> "DefinesRef.scala"
    )

  //SCL-16305
  def testClassObjectNameClash(): Unit = doResolveTest(
    """
      |package hints
      |
      |case class Hint()
      |object Hint
      |""".stripMargin -> "hintsHint.scala",

    s"""
       |package implicits
       |
       |import hints.Hint
       |
       |object ${REFTGT}Hint {
       |  def addTo(hint: Hint) = ???
       |}
       |
       |""".stripMargin -> "implicitsHint.scala",

    s"""
       |import hints.Hint
       |
       |package object implicits {
       |
       |  def add(hint: Hint) = ${REFSRC}Hint.addTo(hint) //object from `implicits` package shadows top-level import
       |}
       |""".stripMargin -> "package.scala"
  )

  //resolves to object from same (default) package
  def testDefaultPackage(): Unit = {
    doResolveTest(
      s"""
         |object Resolvers {
         |  val ${REFTGT}sonatypeSnaps = ???
         |}
         |""".stripMargin -> "Resolvers.scala",

      """
        |package sbt
        |
        |object Resolvers {
        |  val sonatypeSnaps = ???
        |}""".stripMargin -> "sbtResolvers.scala",

      s"""import sbt._
         |
         |class Test {
         |  import Resolvers._
         |
         |  ${REFSRC}sonatypeSnaps
         |}
         |""".stripMargin -> "Test.scala"
    )
  }

  //SCL-16562
  def testClassName_FromWildcardImport_ClashesWith_NotExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Random //also exists in scala.util
         |""".stripMargin -> "Random.scala",
      s"""package org.example.data
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Random = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_FromWildcardImport_ClashesWith_ExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Random //also exists in scala.util
         |""".stripMargin -> "Random.scala",
      s"""package org.example.data
         |
         |import org.example.data.Random
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Random = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_FromWildcardImport_ClashesWith_ExplicitlyImportedClass_FromOtherPackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Random //also exists in scala.util
         |""".stripMargin -> "Random.scala",
      s"""package org.example.usage
         |
         |import org.example.data.Random
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Random = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_DefaultPackage_ClashesWith_NotExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Responder //also exists in scala.lang (default package)
         |""".stripMargin -> "Responder.scala",
      s"""package org.example.data
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Responder = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_DefaultPackage_ClashesWith_ExplicitlyImportedClass_FromSamePackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Responder //also exists in scala.lang (default package)
         |""".stripMargin -> "Responder.scala",
      s"""package org.example.data
         |
         |import org.example.data.Responder
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Responder = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }

  def testClassName_DefaultPackage_ClashesWith_ExplicitlyImportedClass_FromOtherPackage(): Unit = {
    doResolveTest(
      s"""package org.example.data
         |
         |class ${REFTGT}Responder //also exists in scala.lang (default package)
         |""".stripMargin -> "Responder.scala",
      s"""package org.example.usage
         |
         |import org.example.data.Responder
         |
         |import scala.util._
         |
         |object Usage {
         |  val x: ${REFSRC}Responder = ???
         |}
         |""".stripMargin -> "Usage.scala"
    )
  }
}