package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.JavaCompletionUtil.getAllMethods
import org.jetbrains.plugins.scala.base.libraryLoaders.{LibraryLoader, SourcesLoader}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.junit.Assert.assertEquals

class ScalaGlobalMemberCompletionTest extends ScalaCodeInsightTestBase {

  import ScalaCodeInsightTestBase._

  override def getTestDataPath: String =
    s"${super.getTestDataPath}globalMember"

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ SourcesLoader(getTestDataPath)

  def testGlobalMember1(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  rawObj$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import rawObject.RawObject1
        |
        |class TUI {
        |  RawObject1.rawObject()
        |}
      """.stripMargin,
    item = "rawObject",
    time = 2
  )

  def testGlobalMember2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  globalVal$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import rawObject.RawObject2
        |
        |class TUI {
        |  RawObject2.globalValue
        |}
      """.stripMargin,
    item = "globalValue",
    time = 2
  )

  def testGlobalMember3(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  globalVar$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import rawObject.RawObject3
        |
        |class TUI {
        |  RawObject3.globalVariable
        |}
      """.stripMargin,
    item = "globalVariable",
    time = 2
  )

  def testGlobalMember4(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  patVal$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import rawObject.RawObject4
        |
        |class TUI {
        |  RawObject4.patValue
        |}
      """.stripMargin,
    item = "patValue",
    time = 2
  )

  def testGlobalMember5(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  patternVar$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import rawObject.RawObject5
        |
        |class TUI {
        |  RawObject5.patternVariable
        |}
      """.stripMargin,
    item = "patternVariable",
    time = 2
  )

  def testGlobalMember6(): Unit = doCompletionTest(
    fileText =
      s"""
         |import rawObject.RawObject6.importedDef
         |
         |class TUI {
         |  importDe$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |import rawObject.RawObject6.importedDef
        |
        |class TUI {
        |  importedDef()
        |}
        |
      """.stripMargin,
    item = "importedDef",
    time = 2
  )

  def testGlobalMember7(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class TUI {
         |  imposToR$CARET
         |}
       """.stripMargin,
    invocationCount = 2
  )()

  def testGlobalMemberJava(): Unit = doCompletionTest(
    fileText =
      s"""
         |class TUI {
         |  activeCoun$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class TUI {
         |  Thread.activeCount()$CARET
         |}
      """.stripMargin,
    item = "activeCount",
    time = 2
  )

  def testGlobalMemberJava2(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class TUI {
         |  defaultUn$CARET
         |}
      """.stripMargin,
    item = "defaultUncaughtExceptionHandler",
    invocationCount = 2
  )

  def testGlobalMemberJavaAccessAll(): Unit = doCompletionTest(
    fileText =
      s"""class TUI {
         |  defaultUn$CARET
         |}
      """.stripMargin,
    resultText =
      s"""class TUI {
         |  Thread.defaultUncaughtExceptionHandler$CARET
         |}
      """.stripMargin,
    item = "defaultUncaughtExceptionHandler",
    time = 3
  )

  def testGlobalMemberJava3(): Unit = {
    configureFromFileText("sort" + CARET)

    val actual = completeBasic(2)
      .count { lookup =>
        hasItemText(lookup, "sort")(
          itemText = "Collections.sort",
          tailText = "[T](...) (java.util)",
          typeText = "Unit",
        ) && getAllMethods(lookup).size() == 2
      }

    assertEquals(1, actual)
  }

  def testGlobalMember8(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |object BlahBlahBlahContainer {
         |  private def doSmthPrivate() {}
         |  def doSmthPublic() {}
         |}
         |
         |class Test {
         |  def test() {
         |    dsp$CARET
         |  }
         |}
       """.stripMargin,
    item = "doSmthPrivate",
    invocationCount = 2
  )

  def testGlobalMember9(): Unit = doCompletionTest(
    fileText =
      s"""
         |object BlahBlahBlahContainer {
         |  private def doSmthPrivate() {}
         |  def doSmthPublic() {}
         |}
         |
         |class Test {
         |  def test() {
         |    dsp$CARET
         |  }
         |}""".stripMargin,
    resultText =
      s"""
         |object BlahBlahBlahContainer {
         |  private def doSmthPrivate() {}
         |  def doSmthPublic() {}
         |}
         |
         |class Test {
         |  def test() {
         |    BlahBlahBlahContainer.doSmthPrivate()$CARET
         |  }
         |}""".stripMargin,
    item = "doSmthPrivate",
    time = 3
  )

  def testGlobalMember10(): Unit = doCompletionTest(
    fileText =
      s"""trait Foo {
         |  def foo: Int
         |}
         |
         |object Bar extends Foo {
         |  override val foo = 42
         |}
         |
         |object Baz {
         |  f$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""trait Foo {
         |  def foo: Int
         |}
         |
         |object Bar extends Foo {
         |  override val foo = 42
         |}
         |
         |object Baz {
         |  Bar.foo$CARET
         |}
         |""".stripMargin,
    item = "foo",
    time = 2
  )

  def testGlobalMember11(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo
         |
         |object Foo {
         |  def update(key: String, foo: Foo): Foo = foo
         |}
         |
         |object Bar {
         |  u$CARET
         |}
         |""".stripMargin,
    item = "update",
    invocationCount = 2
  )

  def testCompanionObjectMethod(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo {
         |  $CARET
         |}
         |
         |object Foo {
         |  private[this] def foo(foo: Int): Unit = {}
         |
         |  def bar(): Unit = {}
         |}
         |""".stripMargin,
    item = "foo"
  )

  def testImportedCompanionObjectMethod(): Unit = doRawCompletionTest(
    fileText =
      s"""import Foo.bar
         |
         |class Foo {
         |  f$CARET
         |}
         |
         |object Foo {
         |  def foo() = 42
         |  def bar() = 42
         |}
         |""".stripMargin,
    resultText =
      s"""import Foo.{bar, foo}
         |
         |class Foo {
         |  foo()$CARET
         |}
         |
         |object Foo {
         |  def foo() = 42
         |  def bar() = 42
         |}
         |""".stripMargin
  ) {
    hasItemText(_, "foo")(
      tailText = "() (Foo)",
      typeText = "Int",
    )
  }

  def testCompanionObjectUpdateMethod(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo {
         |  u$CARET
         |}
         |
         |object Foo {
         |  def update(key: String, foo: Foo): Foo = foo
         |}
         |""".stripMargin,
    item = "update"
  )

  def testCompanionObjectMethodAccessAll(): Unit = doCompletionTest(
    fileText =
      s"""class Foo {
         |  $CARET
         |}
         |
         |object Foo {
         |  private[this] def foo(foo: Int): Unit = {}
         |
         |  def bar(): Unit = {}
         |}
         |""".stripMargin,
    resultText =
      s"""import Foo.foo
         |
         |class Foo {
         |  foo($CARET)
         |}
         |
         |object Foo {
         |  private[this] def foo(foo: Int): Unit = {}
         |
         |  def bar(): Unit = {}
         |}
         |""".stripMargin,
    item = "foo",
    time = 2
  )

  def testCompanionObjectValue(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  f$CARET
         |}
         |
         |object Foo {
         |  val (_, foo) = (42, 42)
         |}
         |""".stripMargin,
    resultText =
      s"""import Foo.foo
         |
         |class Foo {
         |  foo$CARET
         |}
         |
         |object Foo {
         |  val (_, foo) = (42, 42)
         |}
         |""".stripMargin
  ) {
    hasItemText(_, "foo")(
      tailText = " (Foo)",
      typeText = "Int",
    )
  }

  def testCompanionObjectTypeAlias(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo {
         |  B$CARET
         |}
         |
         |object Foo {
         |  type Bar = Int
         |}
         |""".stripMargin,
    item = "Bar"
  )

  def testCompanionObjectNestedObject(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo {
         |  B$CARET
         |}
         |
         |object Foo {
         |  object Bar
         |}
         |""".stripMargin,
    item = "Bar"
  )

  def testImportedCompanionObjectValue(): Unit = checkNoCompletion(
    fileText =
      s"""class Foo {
         |  import Foo._
         |
         |  f$CARET
         |}
         |
         |object Foo {
         |  val foo = 42
         |}
         |""".stripMargin,
  ) { lookup =>
    hasItemText(lookup, "foo")(tailText = " (Foo)") ||
      hasItemText(lookup, "foo")(
        itemText = "Foo.foo",
        tailText = " <default>"
      )
  }

  def testNestedCompanionObjectValue(): Unit = doCompletionTest(
    fileText =
      s"""class Foo {
         |  class Bar {
         |    f$CARET
         |  }
         |}
         |
         |object Foo {
         |  val (_, foo) = (42, 42)
         |}
         |""".stripMargin,
    resultText =
      s"""import Foo.foo
         |
         |class Foo {
         |  class Bar {
         |    foo$CARET
         |  }
         |}
         |
         |object Foo {
         |  val (_, foo) = (42, 42)
         |}
         |""".stripMargin,
    item = "foo"
  )

  def testCompanionObjectVariableNameCollision(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  val foo = 42
         |  f$CARET
         |}
         |
         |object Foo {
         |  var foo = 42
         |}
         |""".stripMargin,
    resultText =
      s"""class Foo {
         |  val foo = 42
         |  Foo.foo$CARET
         |}
         |
         |object Foo {
         |  var foo = 42
         |}
         |""".stripMargin
  ) {
    hasItemText(_, "foo")(
      itemText = "Foo.foo",
      tailText = " <default>",
      typeText = "Int",
    )
  }

  def testCompanionObjectExtensionLikeMethod(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  class Bar {
         |    val foo = new Foo
         |  }
         |
         |  val bar = new Bar
         |  bar.foo.$CARET
         |}
         |
         |object Foo {
         |  def foo(foo: Foo): Unit = {}
         |}
         |""".stripMargin,
    resultText =
      s"""import Foo.foo
         |
         |class Foo {
         |  class Bar {
         |    val foo = new Foo
         |  }
         |
         |  val bar = new Bar
         |  foo(bar.foo)$CARET
         |}
         |
         |object Foo {
         |  def foo(foo: Foo): Unit = {}
         |}
         |""".stripMargin
  ) {
    hasItemText(_, "foo")(
      tailText = " (Foo)",
      typeText = "Unit",
    )
  }

  def testCompanionObjectExtensionLikeMethod_postfix(): Unit = doRawCompletionTest(
    fileText =
      s"""class Foo {
         |  class Bar {
         |    val foo = new Foo
         |  }
         |
         |  val bar = new Bar
         |  bar.foo $CARET
         |}
         |
         |object Foo {
         |  def foo(foo: Foo): Unit = {}
         |}
         |""".stripMargin,
    resultText =
      s"""import Foo.foo
         |
         |class Foo {
         |  class Bar {
         |    val foo = new Foo
         |  }
         |
         |  val bar = new Bar
         |  foo(bar.foo)$CARET
         |}
         |
         |object Foo {
         |  def foo(foo: Foo): Unit = {}
         |}
         |""".stripMargin
  ) {
    hasItemText(_, "foo")(
      tailText = " (Foo)",
      typeText = "Unit",
    )
  }

  def testCompanionObjectExtensionLikeMethod2(): Unit = checkNoBasicCompletion(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  private[this] def foo(foo: Foo): Unit = {}
         |}
         |
         |object Main {
         |  (_: Foo).f$CARET
         |}
         |""".stripMargin,
    item = "foo"
  )

  def testCompanionObjectExtensionLikeMethod2_postfix(): Unit = checkNoBasicCompletion(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  private[this] def foo(foo: Foo): Unit = {}
         |}
         |
         |object Main {
         |  (_: Foo) f$CARET
         |}
         |""".stripMargin,
    item = "foo"
  )

  def testCompanionObjectExtensionLikeMethodAccessAll(): Unit = doCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  private[this] def foo(foo: Foo): Unit = {}
         |}
         |
         |object Main {
         |  (_: Foo).f$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""import Foo.foo
         |
         |sealed trait Foo
         |
         |object Foo {
         |  private[this] def foo(foo: Foo): Unit = {}
         |}
         |
         |object Main {
         |  foo((_: Foo))$CARET
         |}
         |""".stripMargin,
    item = "foo",
    time = 2
  )

  def testCompanionObjectExtensionLikeMethodAccessAll_postfix(): Unit = doCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Foo {
         |  private[this] def foo(foo: Foo): Unit = {}
         |}
         |
         |object Main {
         |  (_: Foo) f$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""import Foo.foo
         |
         |sealed trait Foo
         |
         |object Foo {
         |  private[this] def foo(foo: Foo): Unit = {}
         |}
         |
         |object Main {
         |  foo((_: Foo))$CARET
         |}
         |""".stripMargin,
    item = "foo",
    time = 2
  )

  def testCompanionObjectExtensionLikeMethod3(): Unit = doCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Bar {
         |  def foo(foo: Foo): Unit = {}
         |
         |  (_: Foo).f$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Bar {
         |  def foo(foo: Foo): Unit = {}
         |
         |  foo((_: Foo))$CARET
         |}
         |""".stripMargin,
    item = "foo"
  )

  def testCompanionObjectExtensionLikeMethod3_postfix(): Unit = doCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |object Bar {
         |  def foo(foo: Foo): Unit = {}
         |
         |  (_: Foo) f$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |object Bar {
         |  def foo(foo: Foo): Unit = {}
         |
         |  foo((_: Foo))$CARET
         |}
         |""".stripMargin,
    item = "foo"
  )

  def testCompanionObjectExtensionLikeMethod4(): Unit = doCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |case object Bar extends Foo
         |
         |object Foo {
         |  def foo(foo: Foo): Unit = {}
         |
         |  Bar.f$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |case object Bar extends Foo
         |
         |object Foo {
         |  def foo(foo: Foo): Unit = {}
         |
         |  foo(Bar)$CARET
         |}
         |""".stripMargin,
    item = "foo"
  )

  def testCompanionObjectExtensionLikeMethod4_postfix(): Unit = doCompletionTest(
    fileText =
      s"""sealed trait Foo
         |
         |case object Bar extends Foo
         |
         |object Foo {
         |  def foo(foo: Foo): Unit = {}
         |
         |  Bar f$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""sealed trait Foo
         |
         |case object Bar extends Foo
         |
         |object Foo {
         |  def foo(foo: Foo): Unit = {}
         |
         |  foo(Bar)$CARET
         |}
         |""".stripMargin,
    item = "foo"
  )

  def testCompanionObjectInvalidExtensionLikeMethodInvalidArgumentsCount(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo {
         |  val foo = new Foo
         |  foo.$CARET
         |}
         |
         |object Foo {
         |  def bar(foo: Foo, bar: Int): Unit = {}
         |}
         |""".stripMargin,
    item = "bar"
  )

  def testCompanionObjectInvalidExtensionLikeMethodInvalidArgumentType(): Unit = checkNoBasicCompletion(
    fileText =
      s"""class Foo {
         |  class Bar
         |
         |  val bar = new Bar
         |  bar.$CARET
         |}
         |
         |object Foo {
         |  def foo(foo: Foo): Unit = {}
         |}
         |""".stripMargin,
    item = "foo"
  )

  def testGlobalMemberInherited(): Unit = {
    configureFromFileText(
      fileText =
        s"""
           |class Base {
           |  def zeeGlobalDefInherited = 0
           |  val zeeGlobalValInherited = 0
           |}
           |
           |object D1 extends Base {
           |  def zeeGlobalDef = 0
           |  def zeeGlobalVal = 0
           |}
           |
           |package object D2 extends Base
           |
           |class Test {
           |  def test() {
           |    zeeGlobal$CARET
           |  }
           |}""".stripMargin
    )

    val actual = completeBasic(3).toSet
      .filterByType[ScalaLookupItem]
      .map { lookup =>
        s"${lookup.containingClassName}.${lookup.getLookupString}"
      }

    val expected = Set(
      "D1.zeeGlobalDef",
      "D1.zeeGlobalVal",
      "D1.zeeGlobalDefInherited",
      "D1.zeeGlobalValInherited"
    )
    assertEquals(expected, actual)
  }

  def testCompanionObjectConversion(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Foo[T]
         |
         |class Bar[T] {
         |  def bar(): Unit = {}
         |}
         |
         |class BarDouble {
         |  def bar(): Unit = {}
         |}
         |
         |object Foo {
         |  implicit def toBar[T](foo: Foo[T]): Bar[T] = new Bar[T]()
         |
         |  implicit def toBarDouble(foo: Foo[Double]): BarDouble = new BarDouble()
         |}
         |
         |val foo = new Foo[Boolean]()
         |foo.$CARET
       """.stripMargin,
    resultText =
      s"""
         |class Foo[T]
         |
         |class Bar[T] {
         |  def bar(): Unit = {}
         |}
         |
         |class BarDouble {
         |  def bar(): Unit = {}
         |}
         |
         |object Foo {
         |  implicit def toBar[T](foo: Foo[T]): Bar[T] = new Bar[T]()
         |
         |  implicit def toBarDouble(foo: Foo[Double]): BarDouble = new BarDouble()
         |}
         |
         |val foo = new Foo[Boolean]()
         |foo.bar()$CARET
       """.stripMargin,
    item = "bar"
  )

  def testCompanionObjectConversion2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Foo[T]
         |
         |class Bar[T] {
         |  def bar(): Unit = {}
         |}
         |
         |class BarDouble {
         |  def bar(): Unit = {}
         |}
         |
         |object Conversions {
         |  implicit def toBar[T](foo: Foo[T]): Bar[T] = new Bar[T]()
         |
         |  implicit def toBarDouble(foo: Foo[Double]): BarDouble = new BarDouble()
         |}
         |
         |val foo = new Foo[Double]()
         |foo.$CARET
       """.stripMargin,
    resultText =
      s"""
         |import Conversions.toBar
         |
         |class Foo[T]
         |
         |class Bar[T] {
         |  def bar(): Unit = {}
         |}
         |
         |class BarDouble {
         |  def bar(): Unit = {}
         |}
         |
         |object Conversions {
         |  implicit def toBar[T](foo: Foo[T]): Bar[T] = new Bar[T]()
         |
         |  implicit def toBarDouble(foo: Foo[Double]): BarDouble = new BarDouble()
         |}
         |
         |val foo = new Foo[Double]()
         |foo.bar()$CARET
       """.stripMargin,
    item = "bar",
    time = 2
  )

  def testImportObjectConversion(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Foo[T]
         |
         |class Bar[T] {
         |  def bar(): Unit = {}
         |}
         |
         |trait BarConversions {
         |  implicit def toBar[T](foo: Foo[T]): Bar[T] = new Bar[T]()
         |}
         |
         |class BarDouble {
         |  def bar(): Unit = {}
         |}
         |
         |trait BarDoubleConversions {
         |  implicit def toBarDouble(foo: Foo[Double]): BarDouble = new BarDouble()
         |}
         |
         |object Conversions extends BarConversions with BarDoubleConversions
         |
         |val foo = new Foo[Boolean]()
         |foo.$CARET
       """.stripMargin,
    resultText =
      s"""
         |import Conversions.toBar
         |
         |class Foo[T]
         |
         |class Bar[T] {
         |  def bar(): Unit = {}
         |}
         |
         |trait BarConversions {
         |  implicit def toBar[T](foo: Foo[T]): Bar[T] = new Bar[T]()
         |}
         |
         |class BarDouble {
         |  def bar(): Unit = {}
         |}
         |
         |trait BarDoubleConversions {
         |  implicit def toBarDouble(foo: Foo[Double]): BarDouble = new BarDouble()
         |}
         |
         |object Conversions extends BarConversions with BarDoubleConversions
         |
         |val foo = new Foo[Boolean]()
         |foo.bar()$CARET
       """.stripMargin,
    item = "bar",
    time = 2
  )

  def testImportObjectConversion2(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Foo[T]
         |
         |class Bar[T] {
         |  def bar(): Unit = {}
         |}
         |
         |trait BarConversions {
         |  implicit def toBar[T](foo: Foo[T]): Bar[T] = new Bar[T]()
         |}
         |
         |class BarDouble {
         |  def bar(): Unit = {}
         |}
         |
         |trait BarDoubleConversions {
         |  implicit def toBarDouble(foo: Foo[Double]): BarDouble = new BarDouble()
         |}
         |
         |object Conversions extends BarConversions with BarDoubleConversions
         |
         |val foo = new Foo[Double]()
         |foo.$CARET
       """.stripMargin,
    resultText =
      s"""
         |import Conversions.toBar
         |
         |class Foo[T]
         |
         |class Bar[T] {
         |  def bar(): Unit = {}
         |}
         |
         |trait BarConversions {
         |  implicit def toBar[T](foo: Foo[T]): Bar[T] = new Bar[T]()
         |}
         |
         |class BarDouble {
         |  def bar(): Unit = {}
         |}
         |
         |trait BarDoubleConversions {
         |  implicit def toBarDouble(foo: Foo[Double]): BarDouble = new BarDouble()
         |}
         |
         |object Conversions extends BarConversions with BarDoubleConversions
         |
         |val foo = new Foo[Double]()
         |foo.bar()$CARET
       """.stripMargin,
    item = "bar",
    time = 2
  )

  def testImportStringInterpolator(): Unit = doCompletionTest(
    fileText =
      s"""
         |object StringInterpol {
         |  implicit class Xy(val sc: StringContext) extends AnyVal {
         |    def xy(args: Any*): BigDecimal = BigDecimal(sc.parts.head)
         |  }
         |}
         |
         |object Test {
         |  xy$CARET"abc"
         |}
       """.stripMargin,
    resultText =
      s"""
         |import StringInterpol.Xy
         |
         |object StringInterpol {
         |  implicit class Xy(val sc: StringContext) extends AnyVal {
         |    def xy(args: Any*): BigDecimal = BigDecimal(sc.parts.head)
         |  }
         |}
         |
         |object Test {
         |  xy$CARET"abc"
         |}
        """.stripMargin,
    item = "xy",
    time = 2
  )

  def testImportableMethod(): Unit = doRawCompletionTest(
    fileText =
      s"""import java.util.Collections.emptyList
         |
         |e$CARET
         |""".stripMargin,
    resultText =
      s"""import java.util.Collections.{emptyList, emptyMap}
         |
         |emptyMap()$CARET
         |""".stripMargin
  ) {
    hasItemText(_, "emptyMap")(
      tailText = "[K, V]() (java.util.Collections)",
      typeText = "util.Map[K, V]",
    )
  }

  def testImportableField(): Unit = doCompletionTest(
    fileText =
      s"""import Thread.currentThread
         |
         |$CARET
         |""".stripMargin,
    resultText =
      s"""import Thread.{currentThread, defaultUncaughtExceptionHandler}
         |
         |defaultUncaughtExceptionHandler$CARET
         |""".stripMargin,
    item = "defaultUncaughtExceptionHandler",
    time = 2
  )

  def testImportableFunction(): Unit = doCompletionTest(
    fileText =
      s"""import Foo.foo
         |
         |object Foo {
         |  def foo(): Unit = {}
         |
         |  def bar(): Unit = {}
         |}
         |
         |b$CARET
         |""".stripMargin,
    resultText =
      s"""import Foo.{bar, foo}
         |
         |object Foo {
         |  def foo(): Unit = {}
         |
         |  def bar(): Unit = {}
         |}
         |
         |bar()$CARET
         |""".stripMargin,
    item = "bar"
  )

  def testImportableValue(): Unit = doCompletionTest(
    fileText =
      s"""import Foo.foo
         |
         |object Foo {
         |  def foo(): Unit = {}
         |
         |  val bar = 42
         |}
         |
         |b$CARET
         |""".stripMargin,
    resultText =
      s"""import Foo.{bar, foo}
         |
         |object Foo {
         |  def foo(): Unit = {}
         |
         |  val bar = 42
         |}
         |
         |bar$CARET
         |""".stripMargin,
    item = "bar"
  )

  def testImportableVariable(): Unit = doCompletionTest(
    fileText =
      s"""import Foo.foo
         |
         |object Foo {
         |  def foo(): Unit = {}
         |
         |  var bar = 42
         |}
         |
         |b$CARET
         |""".stripMargin,
    resultText =
      s"""import Foo.{bar, foo}
         |
         |object Foo {
         |  def foo(): Unit = {}
         |
         |  var bar = 42
         |}
         |
         |bar$CARET
         |""".stripMargin,
    item = "bar"
  )

  def testImportableFromPackageObject(): Unit = doCompletionTest(
    fileText =
      s"""package foo
         |
         |package object bar {
         |  def foo(): Unit = {}
         |
         |  def baz(): Unit = {}
         |}
         |
         |import bar.foo
         |
         |object Bar {
         |  b$CARET
         |}
         |""".stripMargin,
    resultText =
      s"""package foo
         |
         |package object bar {
         |  def foo(): Unit = {}
         |
         |  def baz(): Unit = {}
         |}
         |
         |import bar.{baz, foo}
         |
         |object Bar {
         |  baz()$CARET
         |}
         |""".stripMargin,
    item = "baz"
  )
}
