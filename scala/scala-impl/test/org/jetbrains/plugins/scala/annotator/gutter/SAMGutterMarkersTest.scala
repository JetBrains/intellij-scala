package org.jetbrains.plugins.scala.annotator.gutter

import org.jetbrains.plugins.scala.ScalaVersion

abstract class SAMGutterMarkersTestBase extends GutterMarkersTestBase

// SCL-13925
class SAMGutterMarkersTest_2_13 extends SAMGutterMarkersTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => caret}

  /** For nested cases `wrapper.owner#member` */
  private def refToMember(wrapper: String, owner: String, member: String): String = {
    referenceToElement(s"$wrapper.$owner#$member", member) + " in " +
      referenceToElement(s"$wrapper.$owner", owner) + " in " +
      referenceToElement(wrapper, wrapper)
  }

  def testLambda1Parameter(): Unit =
    doTestSingleTooltipAtCaret(
      s"""trait SAM { def f(s: String): Int }
         |object Usage { val f: SAM = _.length$caret }
         |""".stripMargin,
      "Implements member",
      refToMember("SAM", "f", applyStyleForMember = true)
    )

  def testLambda2Parameters(): Unit =
    doTestSingleTooltipAtCaret(
      s"""trait SAM { def f(x: Int, y: Int): Int }
         |object Usage { val f: SAM = _ + _$caret }
         |""".stripMargin,
      "Implements member",
      refToMember("SAM", "f", applyStyleForMember = true)
    )

  // SCL-15755
  def testNoGuttersForJavaStaticMethodCallForClassWithSAM_AllInOneMix(): Unit = {
    val javaCode =
      """public abstract class JavaAbstractClass {
        |    public abstract String abstractFoo();
        |    public static String staticFoo0() { return null; }
        |    public static String staticFoo1(String s) { return null; }
        |    public static String staticFoo2(String s1, String s2) { return null; }
        |
        |    public static JavaAbstractClass staticBar0() { return null; }
        |    public static JavaAbstractClass staticBar1(String s) { return null; }
        |    public static JavaAbstractClass staticBar2(String s1, String s2) { return null; }
        |}
        |""".stripMargin

    myFixture.addFileToProject("a.java", javaCode)

    // NOTE: This code includes a lot of invalid code, but anyway no gutters should be shown
    // NOTE2: in some cases gutters are shown even if there is a type mismatch error (but the mismatch is in functional types)
    // though scala compiler can show "missing arguments" instead type missmatch in that places
    val scalaCode =
      """val x0: String = JavaAbstractClass.staticFoo0
        |val x1: String = JavaAbstractClass.staticFoo1
        |val x2: String = JavaAbstractClass.staticFoo2
        |val x3: String = JavaAbstractClass.staticBar0
        |val x4: String = JavaAbstractClass.staticBar1
        |val x5: String = JavaAbstractClass.staticBar2
        |
        |val y0: JavaAbstractClass = JavaAbstractClass.staticFoo0
        |val y1: JavaAbstractClass = JavaAbstractClass.staticFoo1
        |val y2: JavaAbstractClass = JavaAbstractClass.staticFoo2
        |val y3: JavaAbstractClass = JavaAbstractClass.staticBar0
        |val y4: JavaAbstractClass = JavaAbstractClass.staticBar1
        |val y5: JavaAbstractClass = JavaAbstractClass.staticBar2
        |
        |val f0 = JavaAbstractClass.staticFoo0 _
        |val f1 = JavaAbstractClass.staticFoo1 _
        |val f2 = JavaAbstractClass.staticFoo2 _
        |val f3 = JavaAbstractClass.staticBar0 _
        |val f4 = JavaAbstractClass.staticBar1 _
        |val f5 = JavaAbstractClass.staticBar2 _
        |
        |val g0: () => String = JavaAbstractClass.staticFoo0
        |val g1: String => String = JavaAbstractClass.staticFoo1
        |val g2: (String, String) => String = JavaAbstractClass.staticFoo2
        |val g3: () => JavaAbstractClass = JavaAbstractClass.staticBar0
        |val g4: String => JavaAbstractClass = JavaAbstractClass.staticBar1
        |val g5: (String, String) => JavaAbstractClass = JavaAbstractClass.staticBar2
        |""".stripMargin

    doTestAllGuttersShort(
      scalaCode,
      Seq(
        ExpectedGutter(9, (362, 379), s"""Implements member ${refToMember("JavaAbstractClass", "abstractFoo", applyStyleForMember = true)}"""),
        ExpectedGutter(10, (419, 436), s"""Implements member ${refToMember("JavaAbstractClass", "abstractFoo", applyStyleForMember = true)}"""),
        ExpectedGutter(12, (533, 550), s"""Implements member ${refToMember("JavaAbstractClass", "abstractFoo", applyStyleForMember = true)}"""),
        ExpectedGutter(13, (590, 607), s"""Implements member ${refToMember("JavaAbstractClass", "abstractFoo", applyStyleForMember = true)}"""),
      ),
      fileExtension = "sc"
    )
  }

  // SCL-15755
  def testNoGuttersForScalaObjectMethodCallForClassWithSAM__AllInOneMix(): Unit = {
    // This code includes a lot of invalid code, but anyway no gutters should be shown
    val scalaCode =
      """abstract class ScalaAbstractClass {
        |  def abstractFoo(): String
        |}
        |
        |object ScalaAbstractClass {
        |  def staticFoo0: String = null
        |  def staticFoo00(): String = null
        |  def staticFoo1(s: String): String = null
        |  def staticFoo2(s1: String, s2: String): String = null
        |  def staticBar0: ScalaAbstractClass = null
        |  def staticBar00(): ScalaAbstractClass = null
        |  def staticBar1(s: String): ScalaAbstractClass = null
        |  def staticBar2(s1: String, s2: String): ScalaAbstractClass = null
        |}
        |
        |val a         = ScalaAbstractClass.staticFoo0
        |val a         = ScalaAbstractClass.staticFoo00
        |val a         = ScalaAbstractClass.staticFoo1
        |val a         = ScalaAbstractClass.staticFoo2
        |
        |val a: String = ScalaAbstractClass.staticBar0
        |val a: String = ScalaAbstractClass.staticBar00
        |val a: String = ScalaAbstractClass.staticBar1
        |val a: String = ScalaAbstractClass.staticBar2
        |
        |val a: ScalaAbstractClass = ScalaAbstractClass.staticFoo0
        |val a: ScalaAbstractClass = ScalaAbstractClass.staticFoo00
        |val a: ScalaAbstractClass = ScalaAbstractClass.staticFoo1
        |val a: ScalaAbstractClass = ScalaAbstractClass.staticFoo2
        |
        |val a: ScalaAbstractClass = ScalaAbstractClass.staticBar0
        |val a: ScalaAbstractClass = ScalaAbstractClass.staticBar00
        |val a: ScalaAbstractClass = ScalaAbstractClass.staticBar1
        |val a: ScalaAbstractClass = ScalaAbstractClass.staticBar2
        |
        |val a = ScalaAbstractClass.staticFoo0 _
        |val a = ScalaAbstractClass.staticFoo00 _
        |val a = ScalaAbstractClass.staticFoo1 _
        |val a = ScalaAbstractClass.staticFoo2 _
        |
        |val a = ScalaAbstractClass.staticBar0 _
        |val a = ScalaAbstractClass.staticBar00 _
        |val a = ScalaAbstractClass.staticBar1 _
        |val a = ScalaAbstractClass.staticBar2 _
        |
        |val a: () => String                           = ScalaAbstractClass.staticFoo0
        |val a: () => String                           = ScalaAbstractClass.staticFoo00
        |val a: String => String                       = ScalaAbstractClass.staticFoo1
        |val a: (String, String) => String             = ScalaAbstractClass.staticFoo2
        |
        |val a: () => ScalaAbstractClass               = ScalaAbstractClass.staticBar0
        |val a: () => ScalaAbstractClass               = ScalaAbstractClass.staticBar00
        |val a: String => ScalaAbstractClass           = ScalaAbstractClass.staticBar1
        |val a: (String, String) => ScalaAbstractClass = ScalaAbstractClass.staticBar2
        |""".stripMargin

    doTestAllGuttersShort(
      scalaCode,
      Seq(
        ExpectedGutter(28, (995, 1013), s"""Implements member ${refToMember("ScalaAbstractClass", "abstractFoo", applyStyleForMember = true)}"""),
        ExpectedGutter(29, (1053, 1071), s"""Implements member ${refToMember("ScalaAbstractClass", "abstractFoo", applyStyleForMember = true)}"""),
        ExpectedGutter(33, (1229, 1247), s"""Implements member ${refToMember("ScalaAbstractClass", "abstractFoo", applyStyleForMember = true)}"""),
        ExpectedGutter(34, (1287, 1305), s"""Implements member ${refToMember("ScalaAbstractClass", "abstractFoo", applyStyleForMember = true)}"""),
      ),
      fileExtension = "sc"
    )
  }

  def testAllInOneMix(): Unit = {
    myFixture.addFileToProject("a.java",
      """public class JSamWrapper {
        |    public abstract static class JSam0 { public abstract String getType0(); }
        |    public abstract static class JSam1 { public abstract String getType1(int x); }
        |
        |    public static String jGetString0() { return "0"; }
        |    public static String jGetString1(int x) { return "1"; }
        |}
        |""".stripMargin)

    val scalaCode =
      """abstract class Sam0 { def method0: String }
        |abstract class Sam00 { def method00(): String }
        |abstract class Sam1 { def method1(x: Int): String }
        |
        |def foo0: String = null
        |def foo00(): String = null
        |def foo1(x: Int): String = null
        |
        |def f: Sam0  = foo0
        |def f: Sam0  = foo0 _
        |def f: Sam0  = foo00
        |def f: Sam0  = foo00 _
        |def f: Sam00  = foo0
        |def f: Sam00  = foo0 _
        |def f: Sam00  = foo00
        |def f: Sam00  = foo00 _
        |
        |def f: () => String = foo0
        |def f: () => String = foo0 _
        |def f: () => String = foo00
        |def f: () => String = foo00 _
        |
        |
        |def f: Sam1  = foo1
        |def f: Sam1  = foo1 _
        |def f: (Int) => String = foo1
        |def f: (Int) => String = foo1 _
        |
        |import JSamWrapper._
        |
        |def f: JSam0  = foo0
        |def f: JSam0  = foo0 _
        |def f: JSam1  = foo1
        |def f: JSam1  = foo1 _
        |
        |def f: JSam0  = jGetString0
        |def f: JSam0  = jGetString0 _
        |def f: JSam1  = jGetString1
        |def f: JSam1  = jGetString1 _
        |
        |def f: JSam0  = () => "42"
        |def f: JSam1  = x => "42"
        |""".stripMargin

    doTestAllGuttersShort(scalaCode, Seq(
      ExpectedGutter(14, (352, 356), s"""Implements member ${refToMember("Sam00", "method00", applyStyleForMember = true)}"""),
      ExpectedGutter(16, (397, 402), s"""Implements member ${refToMember("Sam00", "method00", applyStyleForMember = true)}"""),
      ExpectedGutter(24, (537, 541), s"""Implements member ${refToMember("Sam1", "method1", applyStyleForMember = true)}"""),
      ExpectedGutter(25, (557, 561), s"""Implements member ${refToMember("Sam1", "method1", applyStyleForMember = true)}"""),
      ExpectedGutter(32, (686, 690), s"""Implements member ${refToMember("JSamWrapper", "JSam0", "getType0")}"""),
      ExpectedGutter(33, (709, 713), s"""Implements member ${refToMember("JSamWrapper", "JSam1", "getType1")}"""),
      ExpectedGutter(34, (730, 734), s"""Implements member ${refToMember("JSamWrapper", "JSam1", "getType1")}"""),
      ExpectedGutter(37, (782, 793), s"""Implements member ${refToMember("JSamWrapper", "JSam0", "getType0")}"""),
      ExpectedGutter(38, (812, 823), s"""Implements member ${refToMember("JSamWrapper", "JSam1", "getType1")}"""),
      ExpectedGutter(39, (840, 851), s"""Implements member ${refToMember("JSamWrapper", "JSam1", "getType1")}"""),
      ExpectedGutter(41, (871, 872), s"""Implements member ${refToMember("JSamWrapper", "JSam0", "getType0")}"""),
      ExpectedGutter(42, (898, 899), s"""Implements member ${refToMember("JSamWrapper", "JSam1", "getType1")}"""),
    ))
  }
}

class SAMGutterMarkersTest_2_12 extends SAMGutterMarkersTest_2_13

// SCL-13968: Do not show SAM implementation gutter markers for Scala < 2.12
// (note: SAM would work for 2.11 with -Xexperimental compiler flag)
// Extending 2_12 to reuse all examples from it.
class SAMGutterMarkersTest_2_11 extends SAMGutterMarkersTest_2_12 {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_11

  override protected def doTestSingleTooltipAtCaret(fileText: String, expectedTooltipParts: String*): Unit =
    doTestNoLineMarkers(fileText)

  override protected def doTestAllGuttersShort(fileText: String, expectedGutters: Seq[ExpectedGutter], fileExtension: String): Unit =
    doTestNoLineMarkers(fileText, fileExtension)
}
