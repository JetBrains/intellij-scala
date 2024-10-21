package org.jetbrains.plugins.scala
package debugger.renderers

import com.intellij.debugger.settings.NodeRendererSettings

import java.nio.file.Path

class ScalaClassRendererTest_2_11 extends ScalaClassRendererTestBase(ScalaVersion.Latest.Scala_2_11)

class ScalaClassRendererTest_2_12 extends ScalaClassRendererTestBase(ScalaVersion.Latest.Scala_2_12)

class ScalaClassRendererTest_2_13 extends ScalaClassRendererTestBase(ScalaVersion.Latest.Scala_2_13)

class ScalaClassRendererTest_3_3 extends ScalaClassRendererTestBase(ScalaVersion.Latest.Scala_3_3)

class ScalaClassRendererTest_3_4 extends ScalaClassRendererTestBase(ScalaVersion.Latest.Scala_3_4)

class ScalaClassRendererTest_3_5 extends ScalaClassRendererTestBase(ScalaVersion.Latest.Scala_3_5)

class ScalaClassRendererTest_3_6 extends ScalaClassRendererTestBase(ScalaVersion.Latest.Scala_3_6)

class ScalaClassRendererTest_3_LTS_RC extends ScalaClassRendererTestBase(ScalaVersion.Latest.Scala_3_LTS_RC)

class ScalaClassRendererTest_3_Next_RC extends ScalaClassRendererTestBase(ScalaVersion.Latest.Scala_3_Next_RC)

abstract class ScalaClassRendererTestBase(scalaVersion: ScalaVersion) extends RendererTestBase {
  
  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

  addSourceFile(Path.of("test", "ScalaObject.scala").toString,
    s"""package test
       |
       |object ScalaObject {
       |  private[this] val privateThisVal: Double = 1.0
       |  private val privateVal: Int = 2
       |  private[test] val packagePrivateVal: String = "3"
       |  val publicVal: Char = 'a'
       |
       |  lazy val lazyVal: String = "lazy"
       |
       |  private[this] var privateThisVar: Double = 4.0
       |  private var privateVar: Int = 5
       |  private[test] var packagePrivateVar: String = "6"
       |  var publicVar: Char = 'b'
       |
       |  override def hashCode: Int = 1
       |
       |  def main(args: Array[String]): Unit = {
       |    // Need to use all private variables to avoid compiler optimizations
       |    val myThis = ScalaObject.this
       |    println(privateThisVal) $breakpoint
       |    println(privateVal)
       |    println(privateThisVar)
       |    println(privateVar)
       |  }
       |}""".stripMargin)

  def testScalaObject(): Unit = {
    testClassRenderer("test.ScalaObject")("myThis", "test.ScalaObject$",
      Set(
        "privateThisVal = 1.0",
        "privateVal = 2",
        "packagePrivateVal = 3",
        "publicVal = 'a' 97",
        "lazyVal = lazy",
        "privateThisVar = 4.0",
        "privateVar = 5",
        "packagePrivateVar = 6",
        "publicVar = 'b' 98"
      ))
  }

  addSourceFile(Path.of("test", "ScalaClass.scala").toString,
    s"""package test
       |
       |class ScalaClass(unusedConstructorParam: Int, usedConstructorParam: Int) {
       |  private[this] val privateThisVal: Double = 1.0
       |  private val privateVal: Int = 2
       |  private[test] val packagePrivateVal: String = "3"
       |  val publicVal: Char = 'a'
       |
       |  lazy val lazyVal: String = "lazy"
       |
       |  private[this] var privateThisVar: Double = 4.0
       |  private var privateVar: Int = 5
       |  private[test] var packagePrivateVar: String = "6"
       |  var publicVar: Char = 'b'
       |
       |  override def hashCode: Int = 1
       |
       |  def foo(): Unit = {
       |    // Need to use all private variables to avoid compiler optimizations
       |    val myThis = ScalaClass.this
       |    println(privateThisVal) $breakpoint
       |    println(privateVal)
       |    println(privateThisVar)
       |    println(privateVar)
       |    println(usedConstructorParam)
       |  }
       |}
       |
       |object Main {
       |  def main(args: Array[String]): Unit = {
       |    new ScalaClass(10, 20).foo()
       |  }
       |}""".stripMargin)

  def testScalaClass(): Unit = {
    testClassRenderer("test.Main")("myThis", "test.ScalaClass",
      Set(
        "privateThisVal = 1.0",
        "privateVal = 2",
        "packagePrivateVal = 3",
        "publicVal = 'a' 97",
        "lazyVal = lazy",
        "privateThisVar = 4.0",
        "privateVar = 5",
        "packagePrivateVar = 6",
        "publicVar = 'b' 98",
        "usedConstructorParam = 20"
      ))
  }

  addSourceFile("TransientLazyVal.scala",
    s"""object TransientLazyVal {
       |  class MyClass(val attrs: Seq[String]) extends Serializable {
       |    @transient private lazy val attrsSize = attrs.size
       |
       |    @transient private lazy val attrsSizeSquare = attrsSize * attrsSize
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    val x = new MyClass(Seq("abc", "def"))
       |    println(x) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testTransientLazyVal(): Unit = testClassRenderer("TransientLazyVal")(
    "x", "TransientLazyVal$MyClass", Set(
    "attrs = {$colon$colon@uniqueID}size = 2",
    "attrsSize = 2",
    "attrsSizeSquare = 4"
  ))

  addSourceFile("TransientLazyValInImplicitClass.scala",
    s"""object TransientLazyValInImplicitClass {
       |  implicit class MyImplicitClass(val attrs: Seq[String]) extends Serializable {
       |    @transient private lazy val attrsSize = attrs.size
       |
       |    @transient private lazy val attrsSizeSquare = attrsSize * attrsSize
       |
       |    @transient private lazy val attrsSizeCube = attrsSizeSquare * attrsSize
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    val x: MyImplicitClass = Seq("abc", "def", "ghi")
       |    println(x) $breakpoint
       |  }
       |}
       |""".stripMargin)

  def testTransientLazyValInImplicitClass(): Unit = testClassRenderer("TransientLazyValInImplicitClass")(
    "x", "TransientLazyValInImplicitClass$MyImplicitClass", Set(
    "attrs = {$colon$colon@uniqueID}size = 3",
    "attrsSize = 3",
    "attrsSizeSquare = 9",
    "attrsSizeCube = 27"
  ))

  private val UNIQUE_ID = "uniqueID"

  private def testClassRenderer(mainClassName: String)(
    varName: String,
    className: String,
    expectedChildrenLabels: Set[String]): Unit = {
    rendererTest(mainClassName) { implicit ctx =>
      val (label, childrenLabels) =
        renderLabelAndChildren(varName, renderChildren = true)

      val classRenderer = NodeRendererSettings.getInstance().getClassRenderer
      val typeName = classRenderer.renderTypeName(className)
      val expectedLabel = s"$varName = {$typeName@$UNIQUE_ID}$className"

      assert(label.startsWith(expectedLabel))
      assertEquals(expectedChildrenLabels, childrenLabels.toSet)
    }
  }
}
