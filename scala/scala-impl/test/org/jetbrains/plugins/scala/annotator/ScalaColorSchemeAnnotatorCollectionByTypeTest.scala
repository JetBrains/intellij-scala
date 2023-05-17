package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings.{AliasExportSemantics, ScalaCollectionHighlightingLevel}
import org.jetbrains.plugins.scala.util.RevertableChange.withModifiedSetting
import org.junit.Assert.assertEquals

import scala.collection.immutable.ListSet

class ScalaColorSchemeAnnotatorCollectionByTypeTest extends ScalaColorSchemeAnnotatorCollectionByTypeTestBase {

  def testAnnotateImmutable_NonQualified(): Unit = {
    val text =
      """import scala.collection.immutable.HashMap
        |import scala.collection.immutable.Map
        |
        |class A {
        |  val _ = HashMap()
        |  val _ = HashMap[String, Int]()
        |
        |  //in parameter type
        |  def foo1(param: Map[String, String]) = ???
        |
        |  //in definition/return type
        |  val _: Map[String, String] = ???
        |  def foo2(): Map[Int, Int] = ???
        |
        |  //in type parameter
        |  class Inner[T <: Map[String, String]]
        |
        |  //constructor call via `new`
        |  new HashMap()
        |  new HashMap[String, Int]()
        |}
        |""".stripMargin

    testAnnotations(text, immutableCollectionMessage,
      """Info((101,108),Immutable Collection)
        |Info((121,128),Immutable Collection)
        |Info((185,188),Immutable Collection)
        |Info((252,255),Immutable Collection)
        |Info((292,295),Immutable Collection)
        |Info((354,357),Immutable Collection)
        |Info((413,420),Immutable Collection)
        |Info((429,436),Immutable Collection)
        |""".stripMargin)
  }

  def testAnnotateImmutable_QualifiedName_Fully(): Unit = {
    val text =
      s"""class A {
         |  val _ = scala.collection.immutable.HashMap(1 -> 2, 3 -> 4)
         |  val _ = scala.collection.immutable.HashMap[Int, Int](1 -> 2, 3 -> 4)
         |  val _ = scala.collection.immutable.HashMap.apply("aaa" -> 2, "3" -> 4)
         |  val _ = scala.collection.immutable.HashMap.apply[String, Int]("aaa" -> 2, "3" -> 4)
         |
         |  //in parameter type
         |  def foo1(param: scala.collection.immutable.Map[String, String]) = ???
         |
         |  //in definition/return type
         |  val _: scala.collection.immutable.Map[String, String] = ???
         |  def foo2(): scala.collection.immutable.Map[Int, Int] = ???
         |
         |  //in type parameter
         |  class Inner[T <: scala.collection.immutable.Map[String, String]]
         |
         |  //constructor call via `new`
         |  new scala.collection.immutable.HashMap()
         |}
         |""".stripMargin

    testAnnotations(text, immutableCollectionMessage,
      """Info((47,54),Immutable Collection)
        |Info((108,115),Immutable Collection)
        |Info((179,186),Immutable Collection)
        |Info((252,259),Immutable Collection)
        |Info((369,372),Immutable Collection)
        |Info((463,466),Immutable Collection)
        |Info((530,533),Immutable Collection)
        |Info((619,622),Immutable Collection)
        |Info((705,712),Immutable Collection)
        |""".stripMargin)
  }

  def testAnnotateImmutable_QualifiedName_Partially(): Unit = {
    val text =
      s"""import scala.collection.immutable
         |
         |class A {
         |  val _ = immutable.HashMap(1 -> 2, 3 -> 4)
         |  val _ = immutable.HashMap[Int, Int](1 -> 2, 3 -> 4)
         |  val _ = immutable.HashMap.apply("aaa" -> 2, "3" -> 4)
         |  val _ = immutable.HashMap.apply[String, Int]("aaa" -> 2, "3" -> 4)
         |
         |  //in parameter type
         |  def foo1(param: immutable.Map[String, String]) = ???
         |
         |  //in definition/return type
         |  val _: immutable.Map[String, String] = ???
         |  def foo2(): immutable.Map[Int, Int] = ???
         |
         |  //in type parameter
         |  class Inner[T <: immutable.Map[String, String]]
         |
         |  //constructor call via `new`
         |  new immutable.HashMap()
         |}
         |""".stripMargin

    testAnnotations(text, immutableCollectionMessage,
      """Info((65,72),Immutable Collection)
        |Info((109,116),Immutable Collection)
        |Info((163,170),Immutable Collection)
        |Info((219,226),Immutable Collection)
        |Info((319,322),Immutable Collection)
        |Info((396,399),Immutable Collection)
        |Info((446,449),Immutable Collection)
        |Info((518,521),Immutable Collection)
        |Info((587,594),Immutable Collection)
        |""".stripMargin
    )
  }

  private def withAliasSemantics(mode: AliasExportSemantics)(body: => Unit): Unit = {
    val revertible = withModifiedSetting(
      ScalaProjectSettings.getInstance(getProject)
    )(mode)(_.getAliasSemantics, _.setAliasSemantics(_))

    revertible.run {
      body
    }
  }

  def testAnnotateImmutable_FromPredef_ExportAliasSemantics(): Unit = {
    withAliasSemantics(AliasExportSemantics.Export) {
      val text =
        """class A {
          |  val _ = List(1, 2, 3)
          |  val _ = List[Int](1, 2, 3)
          |  val _ = List.apply[Int](1, 2, 3)
          |
          |  val _ = Map(1 -> 2, 3 -> 4)
          |  val _ = Map[Int, Int](1 -> 2, 3 -> 4)
          |  val _ = Map.apply[String, Int]("1" -> 2, "3" -> 4)
          |}
          |""".stripMargin

      testAnnotations(text, immutableCollectionMessage,
        """Info((20,24),Immutable Collection)
          |Info((44,48),Immutable Collection)
          |Info((73,77),Immutable Collection)
          |Info((109,112),Immutable Collection)
          |Info((139,142),Immutable Collection)
          |Info((179,182),Immutable Collection)
          |""".stripMargin)
    }
  }

  def testAnnotateImmutable_FromPredef_DefinitionAliasSemantics(): Unit = {
    withAliasSemantics(AliasExportSemantics.Definition) {
      val text =
        """class A {
          |  val _ = List(1, 2, 3)
          |  val _ = List[Int](1, 2, 3)
          |  val _ = List.apply[Int](1, 2, 3)
          |
          |  val _ = Map(1 -> 2, 3 -> 4)
          |  val _ = Map[Int, Int](1 -> 2, 3 -> 4)
          |  val _ = Map.apply[String, Int]("1" -> 2, "3" -> 4)
          |}
          |""".stripMargin

      testAnnotations(text, immutableCollectionMessage,
        """Info((20,24),Immutable Collection)
          |Info((44,48),Immutable Collection)
          |Info((73,77),Immutable Collection)
          |Info((109,112),Immutable Collection)
          |Info((139,142),Immutable Collection)
          |Info((179,182),Immutable Collection)
          |""".stripMargin)
    }
  }

  ////////////////////////////////////////////////////
  //
  // Mutable collections
  //
  ////////////////////////////////////////////////////

  def testAnnotateMutable_NonQualified(): Unit = {
    val text =
      """import scala.collection.mutable.HashMap
        |import scala.collection.mutable.Map
        |
        |class A {
        |  val _ = HashMap()
        |  val _ = HashMap[String, Int]()
        |
        |  //in parameter type
        |  def foo1(param: Map[String, String]) = ???
        |
        |  //in definition/return type
        |  val _: Map[String, String] = ???
        |  def foo2(): Map[Int, Int] = ???
        |
        |  //in type parameter
        |  class Inner[T <: Map[String, String]]
        |
        |  //constructor call via `new`
        |  new HashMap()
        |}
        |""".stripMargin

    testAnnotations(text, mutableCollectionMessage,
      """Info((97,104),Mutable Collection)
        |Info((117,124),Mutable Collection)
        |Info((181,184),Mutable Collection)
        |Info((248,251),Mutable Collection)
        |Info((288,291),Mutable Collection)
        |Info((350,353),Mutable Collection)
        |Info((409,416),Mutable Collection)
        |""".stripMargin)
  }

  def testAnnotateMutable_QualifiedName_Fully(): Unit = {
    val text =
      s"""class A {
         |  val _ = scala.collection.mutable.HashMap(1 -> 2, 3 -> 4)
         |  val _ = scala.collection.mutable.HashMap[Int, Int](1 -> 2, 3 -> 4)
         |  val _ = scala.collection.mutable.HashMap.apply("aaa" -> 2, "3" -> 4)
         |  val _ = scala.collection.mutable.HashMap.apply[String, Int]("aaa" -> 2, "3" -> 4)
         |
         |  //in parameter type
         |  def foo1(param: scala.collection.mutable.Map[String, String]) = ???
         |
         |  //in definition/return type
         |  val _: scala.collection.mutable.Map[String, String] = ???
         |  def foo2(): scala.collection.mutable.Map[Int, Int] = ???
         |
         |  //in type parameter
         |  class Inner[T <: scala.collection.mutable.Map[String, String]]
         |
         |  //constructor call via `new`
         |  new scala.collection.mutable.HashMap()
         |}
         |""".stripMargin

    testAnnotations(text, mutableCollectionMessage,
      """Info((45,52),Mutable Collection)
        |Info((104,111),Mutable Collection)
        |Info((173,180),Mutable Collection)
        |Info((244,251),Mutable Collection)
        |Info((359,362),Mutable Collection)
        |Info((451,454),Mutable Collection)
        |Info((516,519),Mutable Collection)
        |Info((603,606),Mutable Collection)
        |Info((687,694),Mutable Collection)
        |""".stripMargin)
  }

  def testAnnotateMutable_QualifiedName_Partially(): Unit = {
    val text =
      s"""import scala.collection.mutable
         |
         |class A {
         |  val _ = mutable.HashMap(1 -> 2, 3 -> 4)
         |  val _ = mutable.HashMap[Int, Int](1 -> 2, 3 -> 4)
         |  val _ = mutable.HashMap.apply("aaa" -> 2, "3" -> 4)
         |  val _ = mutable.HashMap.apply[String, Int]("aaa" -> 2, "3" -> 4)
         |
         |  //in parameter type
         |  def foo1(param: mutable.Map[String, String]) = ???
         |
         |  //in definition/return type
         |  val _: mutable.Map[String, String] = ???
         |  def foo2(): mutable.Map[Int, Int] = ???
         |
         |  //in type parameter
         |  class Inner[T <: mutable.Map[String, String]]
         |
         |  //constructor call via `new`
         |  new mutable.HashMap()
         |}
         |""".stripMargin

    testAnnotations(text, mutableCollectionMessage,
      """Info((61,68),Mutable Collection)
        |Info((103,110),Mutable Collection)
        |Info((155,162),Mutable Collection)
        |Info((209,216),Mutable Collection)
        |Info((307,310),Mutable Collection)
        |Info((382,385),Mutable Collection)
        |Info((430,433),Mutable Collection)
        |Info((500,503),Mutable Collection)
        |Info((567,574),Mutable Collection)
        |""".stripMargin)
  }

  def testAnnotateMutable_FromPredef(): Unit = {
    val text =
      """class A {
        |  val _ = new StringBuilder("blah")
        |  val _ = StringBuilder.newBuilder
        |}
        |""".stripMargin

    testAnnotations(text, mutableCollectionMessage,
      """Info((24,37),Mutable Collection)
        |Info((56,69),Mutable Collection)
        |""".stripMargin)
  }

  ////////////////////////////////////////////////////
  //
  // Java collection
  //
  ////////////////////////////////////////////////////

  def testAnnotateJavaCollection_NonQualified(): Unit = {
    val text =
      """import java.util.ArrayList
        |import java.util.Map
        |import java.util.HashMap
        |
        |class A {
        |  //constructor call via `new`
        |  new ArrayList[String]()
        |  new HashMap()
        |  new HashMap[String, Int]()
        |
        |  //in parameter type
        |  def foo1(param: Map[String, String]) = ???
        |
        |  //in definition/return type
        |  val _: Map[String, String] = ???
        |  def foo2(): Map[Int, Int] = ???
        |
        |  //in type parameter
        |  class Inner[T <: Map[String, String]]
        |}
        |""".stripMargin

    testAnnotations(text, javaCollectionMessage,
      """Info((121,130),Java Collection)
        |Info((147,154),Java Collection)
        |Info((163,170),Java Collection)
        |Info((227,230),Java Collection)
        |Info((294,297),Java Collection)
        |Info((334,337),Java Collection)
        |Info((396,399),Java Collection)
        |""".stripMargin)
  }

  def testAnnotateJavaCollection_QualifiedName_Fully(): Unit = {
    val text =
      s"""class A {
         |  //constructor call via `new`
         |  new java.util.ArrayList[String]()
         |  new java.util.HashMap()
         |  new java.util.HashMap[String, Int]()
         |
         |  //in parameter type
         |  def foo1(param: java.util.Map[String, String]) = ???
         |
         |  //in definition/return type
         |  val _: java.util.Map[String, String] = ???
         |  def foo2(): java.util.Map[Int, Int] = ???
         |
         |  //in type parameter
         |  class Inner[T <: java.util.Map[String, String]]
         |}
         |""".stripMargin

    testAnnotations(text, javaCollectionMessage,
      """Info((57,66),Java Collection)
        |Info((93,100),Java Collection)
        |Info((119,126),Java Collection)
        |Info((193,196),Java Collection)
        |Info((270,273),Java Collection)
        |Info((320,323),Java Collection)
        |Info((392,395),Java Collection)
        |""".stripMargin)
  }

  def testAnnotateJavaCollection_QualifiedName_Partially(): Unit = {
    val text =
      s"""import java.util
         |
         |class A {
         |  //constructor call via `new`
         |  new util.ArrayList[String]()
         |  new util.HashMap()
         |  new util.HashMap[String, Int]()
         |
         |  //in parameter type
         |  def foo1(param: util.Map[String, String]) = ???
         |
         |  //in definition/return type
         |  val _: util.Map[String, String] = ???
         |  def foo2(): util.Map[Int, Int] = ???
         |
         |  //in type parameter
         |  class Inner[T <: util.Map[String, String]]
         |}
         |""".stripMargin

    testAnnotations(text, javaCollectionMessage,
      """Info((70,79),Java Collection)
        |Info((101,108),Java Collection)
        |Info((122,129),Java Collection)
        |Info((191,194),Java Collection)
        |Info((263,266),Java Collection)
        |Info((308,311),Java Collection)
        |Info((375,378),Java Collection)
        |""".stripMargin)
  }

  ////////////////////////////////////////////////////
  //
  // Other
  //
  ////////////////////////////////////////////////////

  def testDoNotAnnotateApplyMethod(): Unit = {
    val text =
      """class A {
        |  val _ = List.apply(1, 2, 3)
        |
        |  {
        |    val _ = scala.collection.immutable.List.apply(1, 2, 3)
        |    val _ = scala.collection.mutable.List.apply(1, 2, 3)
        |  }
        |
        |  {
        |    import scala.collection.immutable
        |    import scala.collection.mutable
        |
        |    val _ = immutable.List.apply(1, 2, 3)
        |    val _ = mutable.List.apply(1, 2, 3)
        |  }
        |}
        |""".stripMargin
    testHasNoAnnotations(
      text,
      MessageWithCode(immutableCollectionMessage, "apply"),
      MessageWithCode(mutableCollectionMessage, "apply"),
    )
  }

  def testDoNotAnnotateImport(): Unit = {
    val text =
      """import scala.collection.mutable.HashMap
        |import scala.collection.immutable.HashMap
        |import java.util.ArrayList
        |
        |class A {
        |}
        |""".stripMargin

    testHasNoAnnotations(text, mutableCollectionMessage, immutableCollectionMessage, javaCollectionMessage)
  }

  def annotateTypeAliasesUsage(): Unit = {
    val text =
      s"""class A {
         |  type MutableScalaMap[A, B] = scala.collection.mutable.HashMap[A, B]
         |  type ImmutableScalaMap[A, B] = scala.collection.immutable.HashMap[A, B]
         |  type JavaMap[A, B] = java.util.HashMap[A, B]
         |
         |  new MutableScalaMap[String, Int]
         |  new ImmutableScalaMap[String, Int]
         |  new JavaMap[String, Int]
         |
         |  val _: MutableScalaMap[String, Int] = ???
         |  val _: ImmutableScalaMap[String, Int] = ???
         |  val _: JavaMap[String, Int] = ???
         |}
         |""".stripMargin

    testAnnotations(text, ListSet(mutableCollectionMessage, immutableCollectionMessage, javaCollectionMessage),
      """""".stripMargin)
  }
}


abstract class ScalaColorSchemeAnnotatorCollectionByTypeTestBase
  extends ScalaColorSchemeAnnotatorTestBase[String] {

  protected val immutableCollectionMessage = ScalaBundle.message("scala.immutable.collection")
  protected val mutableCollectionMessage = ScalaBundle.message("scala.mutable.collection")
  protected val javaCollectionMessage = ScalaBundle.message("java.collection")

  private val revertibleCollectionHighlightingLevel = withModifiedSetting(
    ScalaProjectSettings.getInstance(getProject)
  )(ScalaCollectionHighlightingLevel.All)(
    _.getCollectionTypeHighlightingLevel,
    _.setCollectionTypeHighlightingLevel(_)
  )

  override protected def setUp(): Unit = {
    super.setUp()
    revertibleCollectionHighlightingLevel.applyChange()
  }

  override protected def tearDown(): Unit = {
    revertibleCollectionHighlightingLevel.revertChange()
    super.tearDown()
  }

  override protected def needToAnnotateElement(element: PsiElement): Boolean = {
    //collection-by-type annotations is only run for references, so optimise it
    element.isInstanceOf[ScReference]
  }

  override protected def buildAnnotationsTestText(annotations: Seq[Message2]): String = {
    annotations.map(_.textWithRangeAndMessage).mkString("\n")
  }

  override protected def getFilterByField(annotation: Message2): String =
    annotation.message

  protected case class MessageWithCode(message: String, fileText: String)

  protected def testHasNoAnnotations(
    text: String,
    filterAnnotatedMessagesWithFileText: MessageWithCode*
  )(implicit d: DummyImplicit): Unit = {
    testAnnotations(text, filterAnnotatedMessagesWithFileText.to(ListSet), "")
  }

  protected def testAnnotations(
    text: String,
    filterAnnotationMessagesWithFileText: Set[MessageWithCode],
    expectedAnnotationsText: String
  )(implicit d: DummyImplicit): Unit = {
    val holder = annotateWithColorSchemeAnnotator(text)
    val annotationsWithMatchingMessage = holder.annotations.filter(a => {
      filterAnnotationMessagesWithFileText.contains(MessageWithCode(a.message, a.code))
    })
    val actualAnnotationsText = buildAnnotationsTestText(annotationsWithMatchingMessage)

    assertEquals(
      s"Wrong annotations set for filtered message+fileText ${filterAnnotationMessagesWithFileText.map(m => s"`$m`").mkString(", ")}",
      expectedAnnotationsText.trim,
      actualAnnotationsText.trim
    )
  }
}
