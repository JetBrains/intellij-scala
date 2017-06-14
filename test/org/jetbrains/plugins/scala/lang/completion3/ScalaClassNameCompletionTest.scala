package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => CARET}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion3.ScalaCodeInsightTestBase.{DEFAULT_CHAR, DEFAULT_COMPLETION_TYPE}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}

/**
  * User: Alefas
  * Date: 27.03.12
  */
abstract class ScalaClassNameCompletionTest extends ScalaCodeInsightTestBase {

  protected def predicate(lookup: LookupElement,
                          qualifiedName: String,
                          companionObject: Boolean = false): Boolean =
    Option(lookup).collect {
      case lookup: ScalaLookupItem => lookup
    }.map(_.element).collect {
      case o: ScObject if companionObject => o
      case c: ScClass => c
    }.exists(_.qualifiedName == qualifiedName)

  protected def codeStyleSettings: ScalaCodeStyleSettings =
    ScalaCodeStyleSettings.getInstance(getProject)
}

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

  def testExpressionSameName(): Unit = doCompletionTest(
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
    char = DEFAULT_CHAR,
    time = 2,
    completionType = DEFAULT_COMPLETION_TYPE
  ) {
    predicate(_, "scala.collection.mutable.HashSet", companionObject = true)
  }

  def testClassSameName(): Unit = doCompletionTest(
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
    char = DEFAULT_CHAR,
    time = 2,
    completionType = DEFAULT_COMPLETION_TYPE
  ) {
    predicate(_, "scala.collection.mutable.HashSet")
  }

  def testImportsMess(): Unit = doCompletionTest(
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
        |import scala.collection.immutable.{HashMap => _, _}
        |import scala.collection.mutable._
        |
        |class Test2 {
        |  val x: HashMap[String, String] = HashMap.empty
        |  val z: ListSet = null
        |}
      """.stripMargin,
    char = DEFAULT_CHAR,
    time = 2,
    completionType = DEFAULT_COMPLETION_TYPE
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
}

class ImportsWithPrefixCompletionTest extends ScalaClassNameCompletionTest {

  private var importsWithPrefix: Array[String] = Array.empty

  protected override def setUp(): Unit = {
    super.setUp()

    val settings = codeStyleSettings
    importsWithPrefix = settings.getImportsWithPrefix
    settings.setImportsWithPrefix(Array.empty)
  }

  override protected def tearDown(): Unit = {
    codeStyleSettings.setImportsWithPrefix(importsWithPrefix)

    super.tearDown()
  }

  def testSmartJoining(): Unit = doCompletionTest(
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
    char = DEFAULT_CHAR,
    time = 2,
    completionType = DEFAULT_COMPLETION_TYPE
  ) {
    predicate(_, "scala.collection.mutable.ListMap")
  }
}

class FullQualifiedImportsCompletionTest extends ScalaClassNameCompletionTest {

  private var isAddFullQualifiedImports: Boolean = false

  protected override def setUp(): Unit = {
    super.setUp()

    val settings = codeStyleSettings
    isAddFullQualifiedImports = settings.isAddFullQualifiedImports
    settings.setAddFullQualifiedImports(false)
  }

  override protected def tearDown(): Unit = {
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
