package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.util.ConfigureJavaFile.configureJavaFile
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

abstract class ScalaClassNameCompletionTest extends ScalaCompletionTestBase {

  protected def predicate(lookup: LookupElement,
                          qualifiedName: String,
                          companionObject: Boolean = false): Boolean =
    Option(lookup)
      .map(_.getPsiElement)
      .collect {
        case o: ScObject if companionObject => o
        case c: ScClass => c
      }
      .exists(_.qualifiedName == qualifiedName)

  protected def codeStyleSettings: ScalaCodeStyleSettings =
    ScalaCodeStyleSettings.getInstance(getProject)
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13
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

  def testExpressionSameNameAfterNew(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |import collection.immutable.HashSet
         |
         |object Sandbox extends App {
         |  val x: HashSet[Int] = new HashSet[Int]
         |  new HashSet$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import collection.immutable.HashSet
         |import scala.collection.mutable
         |
         |object Sandbox extends App {
         |  val x: HashSet[Int] = new HashSet[Int]
         |  new mutable.HashSet[$CARET]()
         |}
      """.stripMargin,
    invocationCount = 2
  ) {
    predicate(_, "scala.collection.mutable.HashSet", companionObject = true)
  }

  def testExpressionSameNameAfterNewInsideMethodCall(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |import collection.immutable.HashSet
         |
         |object Sandbox extends App {
         |  val x: HashSet[Int] = new HashSet[Int]
         |  locally {
         |    new HashSet$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |import collection.immutable.HashSet
         |import scala.collection.mutable
         |
         |object Sandbox extends App {
         |  val x: HashSet[Int] = new HashSet[Int]
         |  locally {
         |    new mutable.HashSet[$CARET]()
         |  }
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
    invocationCount = 2
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
      ScalaCompletionTestBase.hasItemText(_, "Foo")(typeText = "")
    }
  }

  // SCL-21466
  def testAutoImportWithoutNew(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Test {
         |  def main(args: Array[String]): Unit = {
         |    StandardCh$CARET
         |  }
         |}
        """.stripMargin,
    resultText =
      s"""import java.nio.charset.StandardCharsets
         |
         |object Test {
         |  def main(args: Array[String]): Unit = {
         |    StandardCharsets$CARET
         |  }
         |}
        """.stripMargin,
    item = "StandardCharsets"
  )

  // SCL-21466
  def testAutoImportObjectWithoutNew(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |object Test {
         |  def main(args: Array[String]): Unit = {
         |    Futu$CARET
         |  }
         |}
        """.stripMargin,
    resultText =
      s"""import scala.concurrent.Future
         |
         |object Test {
         |  def main(args: Array[String]): Unit = {
         |    Future$CARET
         |  }
         |}
        """.stripMargin
  )(lookupItem => ScalaCompletionTestBase.hasLookupString(lookupItem, "Future") && lookupItem.getPsiElement.is[ScObject])
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

  def testClassWithoutNew(): Unit = doCompletionTest(
    fileText =
      s"""package com.example
         |
         |class MyUniversalApplyClass(val ctx: String)
         |
         |object Test:
         |  val x: MyUniversalApplyClass = MyUnAp$CARET
         |""".stripMargin,
    resultText =
      """package com.example
        |
        |class MyUniversalApplyClass(val ctx: String)
        |
        |object Test:
        |  val x: MyUniversalApplyClass = MyUniversalApplyClass()
        |""".stripMargin,
    item = "MyUniversalApplyClass"
  )

  def testClassNameRenamedWithoutNew(): Unit = doCompletionTest(
    fileText =
      s"""
         |import java.util.{ArrayList => BLLLL}
         |object Test extends App {
         |  val al: java.util.List[Int] = BL$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |import java.util.{ArrayList => BLLLL}
         |object Test extends App {
         |  val al: java.util.List[Int] = BLLLL[Int]($CARET)
         |}
      """.stripMargin,
    item = "BLLLL"
  )

  def testDoNotSuggestTraitWithoutNew(): Unit = checkNoBasicCompletion(
    fileText =
      s"""package com.example
         |
         |trait MyUniversalApplyTrait
         |
         |object Test:
         |  val x: MyUniversalApplyTrait = MyUnAp$CARET
         |""".stripMargin,
    item = "MyUniversalApplyTrait"
  )

  override def testExpressionSameName(): Unit = doRawCompletionTest(
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
         |  mutable.HashSet[$CARET]()
         |}
      """.stripMargin,
    invocationCount = 2
  ) {
    predicate(_, "scala.collection.mutable.HashSet", companionObject = true)
  }

  def testExpressionSameNameInsideNew(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |import collection.immutable.HashSet
         |
         |class Wrapper
         |
         |object Sandbox extends App {
         |  val x: HashSet[Int] = new HashSet[Int]
         |  new Wrapper {
         |    HashSet$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |import collection.immutable.HashSet
         |import scala.collection.mutable
         |
         |class Wrapper
         |
         |object Sandbox extends App {
         |  val x: HashSet[Int] = new HashSet[Int]
         |  new Wrapper {
         |    mutable.HashSet[$CARET]()
         |  }
         |}
      """.stripMargin,
    invocationCount = 2
  ) {
    predicate(_, "scala.collection.mutable.HashSet", companionObject = true)
  }

  def testExpressionSameNameInsideNew2(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |import collection.immutable.HashSet
         |
         |object Sandbox extends App {
         |  val x: HashSet[Int] = new HashSet[Int]
         |  new HashSet[Int] {
         |    HashSet$CARET
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |import collection.immutable.HashSet
         |import scala.collection.mutable
         |
         |object Sandbox extends App {
         |  val x: HashSet[Int] = new HashSet[Int]
         |  new HashSet[Int] {
         |    mutable.HashSet[$CARET]()
         |  }
         |}
      """.stripMargin,
    invocationCount = 2
  ) {
    predicate(_, "scala.collection.mutable.HashSet", companionObject = true)
  }

  def testEnumCase(): Unit = doCompletionTest(
    fileText =
      s"""
         |enum Target:
         |  case Definition, Extension, ExtensionMethod
         |
         |def test(target: Target): Unit =
         |  target match
         |    case Extens$CARET
        """.stripMargin,
    resultText =
      s"""
         |import Target.ExtensionMethod
         |
         |enum Target:
         |  case Definition, Extension, ExtensionMethod
         |
         |def test(target: Target): Unit =
         |  target match
         |    case ExtensionMethod$CARET
        """.stripMargin,
    item = "ExtensionMethod"
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
  TestScalaVersion.Scala_2_13
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
    invocationCount = 2
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
    invocationCount = 2
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
    invocationCount = 2
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
    invocationCount = 2
  )

  private val ScalaFileTextInSamePackage =
    s"""package org.example
       |
       |class MyScalaClassPublic {}
       |
       |private class MyScalaClassPackagePrivate {}
       |""".stripMargin

  private val JavaFileTextInSamePackage =
    """package org.example;
      |
      |public class MyJavaClassPublic {}
      |
      |class MyJavaClassPackagePrivate {}
      |""".stripMargin

  def testCompleteAfterNew_ShouldContainDefinitionsFromSamePackage_BothScalaAndJava_FromPackageObject(): Unit = {
    myFixture.addFileToProject("org/example/MyJavaClassPublic.java", JavaFileTextInSamePackage)
    myFixture.addFileToProject("org/example/scala_definitions.scala", ScalaFileTextInSamePackage)

    val fileText =
      s"""package org
         |
         |package object example {
         |  new My$CARET
         |}
         |""".stripMargin

    val (_, items) = activeLookupWithItems(fileText, CompletionType.BASIC)
    val itemsLookupStrings = items.map(_.getLookupString).toSeq
    assertContainsElements(itemsLookupStrings, Seq(
      "MyScalaClassPublic",
      "MyScalaClassPackagePrivate",
      "MyJavaClassPublic",
      "MyJavaClassPackagePrivate",
    ))
  }

  def testCompleteAfterNew_ShouldContainDefinitionsFromSamePackage_BothScalaAndJava_FromClass(): Unit = {
    myFixture.addFileToProject("org/example/scala_definitions.scala", ScalaFileTextInSamePackage)
    myFixture.addFileToProject("org/example/MyJavaClassPublic.java", JavaFileTextInSamePackage)

    val fileText =
      s"""package org.example
         |
         |class Example {
         |  new My$CARET
         |}
         |""".stripMargin

    val (_, items) = activeLookupWithItems(fileText, CompletionType.BASIC)
    val itemsLookupStrings = items.map(_.getLookupString).toSeq
    assertContainsElements(itemsLookupStrings, Seq(
      "MyScalaClassPublic",
      "MyScalaClassPackagePrivate",
      "MyJavaClassPublic",
      "MyJavaClassPackagePrivate",
    ))
  }

  private def assertContainsElements[T](collection: Seq[_ <: T], expected: Seq[_ <: T]): Unit = {
    import scala.jdk.CollectionConverters.SeqHasAsJava
    UsefulTestCase.assertContainsElements(collection.asJava, expected.asJava)
  }
}
