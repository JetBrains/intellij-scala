package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}

/**
  * User: Alefas
  * Date: 27.03.12
  */

class ScalaClassNameCompletionTest extends ScalaCodeInsightTestBase {
  def withRelativeImports(body: => Unit): Unit = {
    val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(getProjectAdapter)
    val oldValue = settings.isAddFullQualifiedImports
    settings.setAddFullQualifiedImports(false)
    try {
      body
    } finally {
      settings.setAddFullQualifiedImports(oldValue)
    }
  }

  def testClassNameRenamed() {
    val fileText =
      """
        |import java.util.{ArrayList => BLLLL}
        |object Test extends App {
        |  val al: java.util.List[Int] = new BL<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(2, CompletionType.BASIC)

    val resultText =
      """
        |import java.util.{ArrayList => BLLLL}
        |object Test extends App {
        |  val al: java.util.List[Int] = new BLLLL[Int](<caret>)
        |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "BLLLL").get, '\t')
    checkResultByText(resultText)
  }

  def testExpressionSameName() {
    val fileText =
      """
        |import collection.immutable.HashSet
        |
        |object Sandbox extends App {
        |  val x: HashSet[Int] = new HashSet[Int]
        |  HashSet<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(2, CompletionType.BASIC)

    val resultText =
      """
        |import collection.immutable.HashSet
        |import scala.collection.mutable
        |
        |object Sandbox extends App {
        |  val x: HashSet[Int] = new HashSet[Int]
        |  mutable.HashSet<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find {
      case le: ScalaLookupItem =>
        le.element match {
          case c: ScObject if c.qualifiedName == "scala.collection.mutable.HashSet" => true
          case _ => false
        }
      case _ => false
    }.get, '\t')
    checkResultByText(resultText)
  }

  def testClassSameName() {
    val fileText =
      """
        |import collection.immutable.HashSet
        |
        |object Sandbox extends App {
        |  val x: HashSet[Int] = new HashSet[Int]
        |  val y: HashSet<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(2, CompletionType.BASIC)

    val resultText =
      """
        |import collection.immutable.HashSet
        |import scala.collection.mutable
        |
        |object Sandbox extends App {
        |  val x: HashSet[Int] = new HashSet[Int]
        |  val y: mutable.HashSet<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find {
      case le: ScalaLookupItem =>
        le.element match {
          case c: ScClass if c.qualifiedName == "scala.collection.mutable.HashSet" => true
          case _ => false
        }
      case _ => false
    }.get, '\t')
    checkResultByText(resultText)
  }

  def testSmartJoining() {
    val settings = ScalaCodeStyleSettings.getInstance(getProjectAdapter)
    val oldValue = settings.getImportsWithPrefix
    settings.setImportsWithPrefix(Array.empty)
    try {
      val fileText =
        """
          |import collection.mutable.{Builder, Queue}
          |import scala.collection.immutable.HashMap
          |import collection.mutable.ArrayBuffer
          |
          |object Sandbox extends App {
          |  val m: ListM<caret>
          |}
        """.stripMargin.replaceAll("\r", "").trim()
      configureFromFileTextAdapter("dummy.scala", fileText)
      val (activeLookup, _) = complete(2, CompletionType.BASIC)

      val resultText =
        """
          |import collection.mutable.{ArrayBuffer, Builder, ListMap, Queue}
          |import scala.collection.immutable.HashMap
          |
          |object Sandbox extends App {
          |  val m: ListMap
          |}
        """.stripMargin.replaceAll("\r", "").trim()

      completeLookupItem(activeLookup.find {
        case le: ScalaLookupItem =>
          le.element match {
            case c: ScClass if c.qualifiedName == "scala.collection.mutable.ListMap" => true
            case _ => false
          }
        case _ => false
      }.get, '\t')
      checkResultByText(resultText)
    }
    catch {
      case t: Exception => settings.setImportsWithPrefix(oldValue)
    }
  }

  def testImportsMess() {
    val fileText =
      """
        |import scala.collection.immutable.{BitSet, HashSet, ListMap, SortedMap}
        |import scala.collection.mutable._
        |
        |class Test2 {
        |  val x: HashMap[String, String] = HashMap.empty
        |  val z: ListSet<caret> = null
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(2, CompletionType.BASIC)

    val resultText =
      """
        |import scala.collection.immutable.{HashMap => _, _}
        |import scala.collection.mutable._
        |
        |class Test2 {
        |  val x: HashMap[String, String] = HashMap.empty
        |  val z: ListSet = null
        |}
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find {
      case le: ScalaLookupItem =>
        le.element match {
          case c: ScClass if c.qualifiedName == "scala.collection.immutable.ListSet" => true
          case _ => false
        }
      case _ => false
    }.get, '\t')
    checkResultByText(resultText)
  }

  def testImplicitClass() {
    val fileText =
      """
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
        |  1.<caret>
        |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(2, CompletionType.BASIC)

    val resultText =
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
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(_.getLookupString == "foo").get, '\t')
    checkResultByText(resultText)
  }

  def testSCL4087() {
    withRelativeImports {
      val fileText =
        """
          |package a.b {
          |
          |  class XXXX
          |
          |}
          |
          |import a.{b => c}
          |
          |trait Y {
          |  val x: XXXX<caret>
          |}
        """.stripMargin.replaceAll("\r", "").trim()
      configureFromFileTextAdapter("dummy.scala", fileText)
      val (activeLookup, _) = complete(2, CompletionType.BASIC)

      val resultText =
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
        """.stripMargin.replaceAll("\r", "").trim()

      completeLookupItem(activeLookup.find(_.getLookupString == "XXXX").get, '\t')
      checkResultByText(resultText)
    }
  }

  def testSCL4087_2() {
    withRelativeImports {
      val fileText =
        """
          |package a.b.z {
          |
          |  class XXXX
          |
          |}
          |
          |import a.{b => c}
          |
          |trait Y {
          |  val x: XXXX<caret>
          |}
        """.stripMargin.replaceAll("\r", "").trim()
      configureFromFileTextAdapter("dummy.scala", fileText)
      val (activeLookup, _) = complete(2, CompletionType.BASIC)

      val resultText =
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
        """.stripMargin.replaceAll("\r", "").trim()

      completeLookupItem(activeLookup.find(_.getLookupString == "XXXX").get, '\t')
      checkResultByText(resultText)
    }
  }

}
