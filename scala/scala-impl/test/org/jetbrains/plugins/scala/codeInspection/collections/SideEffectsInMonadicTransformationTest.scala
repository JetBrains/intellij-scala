package org.jetbrains.plugins.scala
package codeInspection
package collections

class SideEffectsInMonadicTransformationTest extends OperationsOnCollectionInspectionTest {

  override protected val classOfInspection: Class[_ <: OperationOnCollectionInspection] =
    classOf[SideEffectsInMonadicTransformationInspection]

  override protected val hint: String =
    ScalaInspectionBundle.message("side.effects.in.monadic")

  def testInfixAssignment(): Unit = {
    checkTextHasError(
      s"""
        |var a = 0
        |Seq(1, 2).map(${START}a += _$END)
      """.stripMargin)
  }

  def testAssignment(): Unit = {
    checkTextHasError(
      s"""
         |var filtered = 0
         |Seq(1, 2).filter { x =>
         |  if (x % 2 == 0) {
         |    ${START}filtered = filtered + 1$END
         |    true
         |  }
         |  else false
         |}
       """.stripMargin)
  }

  def testInnerVar(): Unit ={
    checkTextHasNoErrors(
      """
        |Seq(1, 2).map { x =>
        |  var b = true
        |  if (x == 1) {
        |    b = false
        |  }
        |  x + 1
        |}
      """.stripMargin)
  }

  def testIteratorNext(): Unit = {
    checkTextHasError(
      s"""
        |val it = Iterator(1, 2)
        |Seq(1, 2) map {x =>
        |  if (it.hasNext) x + ${START}it.next$END
        |  else x
        |}
      """.stripMargin
    )
  }

  def testCollectionMethods(): Unit = {
    checkTextHasError(
      s"""
         |import scala.collection.mutable.ArrayBuffer
         |
         |val buf = ArrayBuffer(1, 2)
         |Seq(1, 2).map {x =>
         |  ${START}buf += x$END
         |  x
         |}
      """.stripMargin
    )

    checkTextHasError(
      s"""
         |import scala.collection.mutable.ArrayBuffer
         |
         |val buf = ArrayBuffer(1, 2)
         |Seq(1, 2).map {x =>
         |  ${START}buf.+=:(x)$END
         |  x
         |}
      """.stripMargin
    )

    checkTextHasError(
      s"""
         |import scala.collection.mutable.ArrayBuffer
         |
         |val buf = ArrayBuffer(1, 2)
         |Seq(1, 2).map {x =>
         |  ${START}buf.append(x)$END
         |  x
         |}
      """.stripMargin
    )

    checkTextHasError(
      s"""
         |import scala.collection.mutable.ArrayBuffer
         |
         |val buf = ArrayBuffer(1, 2)
         |Seq(1, 2).map {x =>
         |  ${START}buf.clear$END
         |  x
         |}
      """.stripMargin
    )

    checkTextHasError(
      s"""
         |import scala.collection.mutable.Stack
         |
         |val st = Stack(1, 2)
         |Seq(1, 2).map {x =>
         |  ${START}st.push(x)$END
         |  x
         |}
      """.stripMargin
    )

  }

  def testUpdateMethod(): Unit = {
    checkTextHasError(
      s"""
       |import scala.collection.mutable.ArrayBuffer
       |val buf = ArrayBuffer(1, 2)
       |Seq(1, 2).zipWithIndex.map{
       |  case (x, i) =>
       |    ${START}buf(i) = x$END
       |    x
       |  case _ => 1
       |}
      """.stripMargin)

    checkTextHasNoErrors(
      s"""
         |import scala.collection.mutable.ArrayBuffer
         |Seq(1, 2).zipWithIndex.map{
         |  case (x, i) =>
         |    val buf = ArrayBuffer(1, 2)
         |    buf(i) = x
         |    x
         |  case _ => 1
         |}
      """.stripMargin
    )
  }

  def testUnitTypeMethod(): Unit = {
    checkTextHasError(
      s"""
          |val s = ""
          |Seq(1, 2).map {x =>
          |  ${START}s.wait(1000)$END
          |  x
          |}
         """.stripMargin)

    checkTextHasError(
      s"""
        |Seq("1", "2").map {
        |    ${START}println(_)$END
        |}
      """.stripMargin)

    checkTextHasNoErrors(
      """
        |def foo(i: Int) {
        |   println(i)
        |}
        |val x: Seq[(Int) => Unit] = Seq(1, 2).map(x => foo _)
      """.stripMargin)
  }

  def testScalaSetter(): Unit = {
    checkTextHasError(
      s"""
        |class A {
        |  var z = 1
        |}
        |val a = new A()
        |Seq(1).map(x => ${START}a.z_=(x)$END)
      """.stripMargin
    )
    checkTextHasError(
      s"""
       |class A {
       |  var z = 1
       |}
       |val a = new A()
       |Seq(1).map(x => ${START}a.z = x$END)
      """.stripMargin
    )
  }

  def testJavaSetter(): Unit = {
    checkTextHasError(
      s"""
       |class A {
       |  @BeanProperty
       |  var z = 1
       |}
       |val a = new A()
       |Seq(1).map(x => ${START}a.setZ(x)$END)
      """.stripMargin
    )

    checkTextHasNoErrors(
      s"""
         |class A {
         |  var z = 1
         |
         |  def setZ(z: Int) {
         |    this.z = z
         |  }
         |}
         |
         |Seq(1).map {x =>
         |  val a = new A()
         |  a.setZ(x)
         |}
      """.stripMargin
    )
  }

  def testExpressionsInOtherClasses(): Unit = {
    checkTextHasNoErrors(
      """
        |Seq(1, 2).filter { x =>
        |  Seq(3, 4).foreach {
        |    y => println(y)
        |  }
        |  true
        |}
      """.stripMargin)

    checkTextHasNoErrors(
      """
        |Seq(1, 2).filter { x =>
        |  class Inner {
        |    def foo() = println(x)
        |  }
        |  true
        |}
      """.stripMargin)
  }

}
