package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.util.ConfigureJavaFile.configureJavaFile
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

abstract class ScalaClassNameCompletionTest extends ScalaCodeInsightTestBase {

  protected def predicate(lookup: LookupElement,
                          qualifiedName: String,
                          companionObject: Boolean = false): Boolean =
    Option(lookup).collect {
      case lookup: ScalaLookupItem => lookup
    }.map(_.getPsiElement).collect {
      case o: ScObject if companionObject => o
      case c: ScClass => c
    }.exists(_.qualifiedName == qualifiedName)

  protected def codeStyleSettings: ScalaCodeStyleSettings =
    ScalaCodeStyleSettings.getInstance(getProject)
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12
))
class ClassNameCompletionTest extends ScalaClassNameCompletionTest {

  def testClassNameRenamed(): Unit = doCompletionTest(
    fileText =
      s"""
         |import java.util.{ArrayList => BLLLL}
         |object Test extends App {
         |  val al: java.util.List[Int] = new BL$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import java.util.{ArrayList => BLLLL}
         |object Test extends App {
         |  val al: java.util.List[Int] = new BLLLL[Int]($CARET)
         |}
      """.stripMargin,
    item = "BLLLL"
  )

  def testExpressionSameName(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |import collection.immutable.HashSet
         |
         |object Sandbox extends App {
         |  val x: HashSet[Int] = new HashSet[Int]
         |  HashSet$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import collection.immutable.HashSet
         |import scala.collection.mutable
         |
         |object Sandbox extends App {
         |  val x: HashSet[Int] = new HashSet[Int]
         |  mutable.HashSet$CARET
         |}
      """.stripMargin,
    invocationCount = 2
  ) {
    predicate(_, "scala.collection.mutable.HashSet", companionObject = true)
  }

  def testClassSameName(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |import collection.immutable.HashSet
         |
         |object Sandbox extends App {
         |  val x: HashSet[Int] = new HashSet[Int]
         |  val y: HashSet$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import collection.immutable.HashSet
         |import scala.collection.mutable
         |
         |object Sandbox extends App {
         |  val x: HashSet[Int] = new HashSet[Int]
         |  val y: mutable.HashSet$CARET
         |}
      """.stripMargin,
    invocationCount = 2
  ) {
    predicate(_, "scala.collection.mutable.HashSet")
  }

  def testImportsMess(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |import scala.collection.immutable.{BitSet, HashSet, ListMap, SortedMap}
         |import scala.collection.mutable._
         |
         |class Test2 {
         |  val x: HashMap[String, String] = HashMap.empty
         |  val z: ListSet$CARET = null
         |}
      """.stripMargin,
    resultText =
      """
        |import scala.collection.immutable.{BitSet, HashSet, ListMap, ListSet, SortedMap}
        |import scala.collection.mutable._
        |
        |class Test2 {
        |  val x: HashMap[String, String] = HashMap.empty
        |  val z: ListSet = null
        |}
      """.stripMargin,
    invocationCount = 2
  ) {
    predicate(_, "scala.collection.immutable.ListSet")
  }

  def testImplicitClass(): Unit = doCompletionTest(
    fileText =
      s"""
         |package a
         |
         |object A {
         |
         |  implicit class B(i: Int) {
         |    def foo = 1
         |  }
         |
         |}
         |
         |object B {
         |  1.$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |package a
        |
        |import a.A.B
        |
        |object A {
        |
        |  implicit class B(i: Int) {
        |    def foo = 1
        |  }
        |
        |}
        |
        |object B {
        |  1.foo
        |}
      """.stripMargin,
    item = "foo",
    time = 2
  )

  def testSpaceInClassParents(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Foo
         |class Bar extends $CARET{}
      """.stripMargin,
    resultText =
      s"""
         |class Foo
         |class Bar extends Foo {}
      """.stripMargin,
    item = "Foo"
  )

  def testJavaClassLocation(): Unit = {
    configureJavaFile(
      fileText =
        "public class Foo",
      className = "Foo"
    )

    doRawCompletionTest(
      fileText = s"val foo: $CARET = null",
      resultText = s"val foo: Foo$CARET = null"
    ) {
      ScalaCodeInsightTestBase.hasItemText(_, "Foo")(typeText = "")
    }
  }
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
class ClassNameCompletionTest_Scala_3 extends ClassNameCompletionTest {
  override def testImportsMess(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |import scala.collection.immutable.{BitSet, HashSet, ListMap, SortedMap}
         |import scala.collection.mutable._
         |
         |class Test2 {
         |  val x: HashMap[String, String] = HashMap.empty
         |  val z: ListSet$CARET = null
         |}
      """.stripMargin,
    resultText =
      """
        |import scala.collection.immutable.{BitSet, HashSet, ListMap, ListSet, SortedMap}
        |import scala.collection.mutable.*
        |
        |class Test2 {
        |  val x: HashMap[String, String] = HashMap.empty
        |  val z: ListSet = null
        |}
      """.stripMargin,
    invocationCount = 2
  ) {
    predicate(_, "scala.collection.immutable.ListSet")
  }
}

class ImportsWithPrefixCompletionTest extends ScalaClassNameCompletionTest {

  private var importsWithPrefix: Array[String] = Array.empty

  protected override def setUp(): Unit = {
    super.setUp()

    val settings = codeStyleSettings
    importsWithPrefix = settings.getImportsWithPrefix
    settings.setImportsWithPrefix(Array.empty)
  }

  override def tearDown(): Unit = {
    codeStyleSettings.setImportsWithPrefix(importsWithPrefix)

    super.tearDown()
  }

  def testSmartJoining(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |import collection.mutable.{Builder, Queue}
         |import scala.collection.immutable.HashMap
         |import collection.mutable.ArrayBuffer
         |
         |object Sandbox extends App {
         |  val m: ListM$CARET
         |}
    """.stripMargin,
    resultText =
      """
        |import collection.mutable.{ArrayBuffer, Builder, ListMap, Queue}
        |import scala.collection.immutable.HashMap
        |
        |object Sandbox extends App {
        |  val m: ListMap
        |}
      """.stripMargin,
    invocationCount = 2
  ) {
    predicate(_, "scala.collection.mutable.ListMap")
  }
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12
))
class FullQualifiedImportsCompletionTest extends ScalaClassNameCompletionTest {

  private var isAddFullQualifiedImports: Boolean = false

  protected override def setUp(): Unit = {
    super.setUp()

    val settings = codeStyleSettings
    isAddFullQualifiedImports = settings.isAddFullQualifiedImports
    settings.setAddFullQualifiedImports(false)
  }

  override def tearDown(): Unit = {
    codeStyleSettings.setAddFullQualifiedImports(isAddFullQualifiedImports)

    super.tearDown()
  }

  def testSCL4087(): Unit = doCompletionTest(
    fileText =
      s"""
         |package a.b {
         |
         |  class XXXX
         |
         |}
         |
         |import a.{b => c}
         |
         |trait Y {
         |  val x: XXXX$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |package a.b {
        |
        |  class XXXX
        |
        |}
        |
        |import a.{b => c}
        |import c.XXXX
        |
        |trait Y {
        |  val x: XXXX
        |}
      """.stripMargin,
    item = "XXXX",
    time = 2
  )

  def testSCL4087_2(): Unit = doCompletionTest(
    fileText =
      s"""
         |package a.b.z {
         |
         |  class XXXX
         |
         |}
         |
         |import a.{b => c}
         |
         |trait Y {
         |  val x: XXXX$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |package a.b.z {
        |
        |  class XXXX
        |
        |}
        |
        |import a.{b => c}
        |import c.z.XXXX
        |
        |trait Y {
        |  val x: XXXX
        |}
      """.stripMargin,
    item = "XXXX",
    time = 2
  )
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
class FullQualifiedImportsCompletionTest_Scala_3 extends FullQualifiedImportsCompletionTest {
  override def testSCL4087(): Unit = doCompletionTest(
    fileText =
      s"""
         |package a.b {
         |
         |  class XXXX
         |
         |}
         |
         |import a.{b => c}
         |
         |trait Y {
         |  val x: XXXX$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |package a.b {
        |
        |  class XXXX
        |
        |}
        |
        |import a.b as c
        |import c.XXXX
        |
        |trait Y {
        |  val x: XXXX
        |}
      """.stripMargin,
    item = "XXXX",
    time = 2
  )

  override def testSCL4087_2(): Unit = doCompletionTest(
    fileText =
      s"""
         |package a.b.z {
         |
         |  class XXXX
         |
         |}
         |
         |import a.{b => c}
         |
         |trait Y {
         |  val x: XXXX$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |package a.b.z {
        |
        |  class XXXX
        |
        |}
        |
        |import a.b as c
        |import c.z.XXXX
        |
        |trait Y {
        |  val x: XXXX
        |}
      """.stripMargin,
    item = "XXXX",
    time = 2
  )

}