package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.junit.Assert

/**
 * @author Alefas
 * @since 23.03.12
 */

class ScalaCompletionOrderTest extends ScalaTestCompletionWithOrder {
  def testInImportSelector() {
    val fileText =
      """
        |class B {
        |  def foo2 = 2
        |}
        |class A extends B {
        |  def foo3 = 1
        |}
        |class C {
        |  def foo1 = 3
        |}
        |implicit def a2c(a: A): C = new C
        |val a: A
        |a.foo<caret>
      """

    checkResultWithOrder(Array[AnyRef]("foo3", "foo2", "foo1"), fileText)
  }

  def testLocalBefore(): Unit = {
    val fileText =
      """
        |class LocalBefore {
        |  val field1 = 45
        |  def fiFoo = ???
        |  val fil1, fil2: Int
        |  def foo2() = {
        |    val fiValue = 67
        |    fi<caret>
        |  }
        |}
      """
    checkResultWithOrder(Array[AnyRef]("fiValue", "field1", "fil1", "fil2", "fiFoo"), fileText)
  }

  def testCurrentMemberBeforeAncestorMember(): Unit = {
    val fileText =
      """
        |object InInheritors {
        |      class A {
        |        def fooa = 45
        |        val fos = "sdf"
        |      }
        |      class B extends A {
        |        def fob = 45
        |        val fol = new File("sdff")
        |      }
        |      class C extends B {
        |        def foo = 45
        |        val fok = new Exception
        |      }
        |      (new C).fo<caret>"""

    checkResultWithOrder(Array[AnyRef]("fok", "foo", "fol", "fos", "fob", "fooa"), fileText)
  }

  def testCaseClauseParamAsLocal(): Unit = {
    val fileText =
      """
        |abstract case class Base(i: Int)
        |case class A(b: Int) extends Base(b)
        |class CaseClauseAsLocal(classParam: Base) {
        |  def testCase = {
        |    classParam match {
        |      case A(retparam) =>
        |        ret<caret>
        |      case _ =>
        |    }
        |  }
        |  val retField = 45
        |}
      """

    checkResultWithOrder(Array[AnyRef]("retparam", "retField"), fileText)
  }

  def testScalaTypeBeforeJava(): Unit = {
    val fileText =
      """
        |import java.io.File
        |
        |object ScalaTypeBeforeJava {
        |  type FilTypealias = Int
        |
        |  class FilClass {}
        |
        |  object FilObject{}
        |
        |  def foo = {
        |    val file = new File("file")
        |    val t:Fil<caret>
        |  }
        |}
      """

    checkResultWithOrder(Array[AnyRef]("FilClass", "FilTypealias", "FilObject", "File"), fileText)
  }

  def testLocaBeforeNameParams(): Unit = {
    val fileText =
      """
        |object LocalBeforeNamedParams {
        |  def foo = {
        |    val namelocal = "sdfsd"
        |    def printName(nameParam: String = "Unknown") {
        |      print(nameParam)
        |    }
        |
        |    printName(name<caret>)
        |  }
        |}
      """

    checkResultWithOrder(Array[AnyRef]("namelocal", "nameParam"), fileText)
  }

  def testChooseTypeWhenItExpected():Unit = {
    val fileText =
      """
        |class TypeExpected {
        |  type fiTInClassType = Long
        |  val field1 = 45
        |  def fiFoo = ???
        |  def foo2(fiParam: Int) = {
        |    type fiType = Int
        |    trait fiTrait
        |    case class fiTCase()
        |    val variable: fiT<caret>
        |  }
        |}
      """
    checkResultWithOrder(Array[AnyRef]("fiTCase", "fiTrait", "fiType", "fiTInClassType"), fileText)
  }
}
