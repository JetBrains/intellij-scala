package org.jetbrains.plugins.scala.conversion.copy

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.openapi.util.Disposer
import com.intellij.ui.UiInterceptors
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.actions.editor.copy.CopyPasteTestBase
import org.jetbrains.plugins.scala.lang.refactoring.Associations
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.assertions.CollectionsAssertions.assertCollectionEquals

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Promise}

abstract class CopyScalaToScala_WithAutoImportsTestBase extends CopyPasteTestBase {
  override protected def setUp(): Unit = {
    super.setUp()
    ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE = CodeInsightSettings.YES
  }
}

class CopyScalaToScala_WithAutoImportsTest_Scala2 extends CopyScalaToScala_WithAutoImportsTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  def testAddImportsOnPaste(): Unit = {
    doTest(
      s"""import java.util
         |import scala.collection.mutable.ArraySeq
         |import scala.util.Random
         |import java.util.Deque
         |
         |//noinspection ReferenceMustBePrefixed,ScalaUnusedExpression
         |${START}trait Example {
         |  null: util.ArrayList[Int]
         |  null: IndexedSeq[Int]
         |  null: ArraySeq[Int]
         |  null: Random
         |  null: Deque[Int]
         |}$END
         |""".stripMargin,
      "",
      """import java.util
        |import java.util.Deque
        |import scala.collection.mutable.ArraySeq
        |import scala.util.Random
        |
        |trait Example {
        |  null: util.ArrayList[Int]
        |  null: IndexedSeq[Int]
        |  null: ArraySeq[Int]
        |  null: Random
        |  null: Deque[Int]
        |}""".stripMargin
    )
  }

  def testAddImportsOnPaste_RenamedImports(): Unit = {
    doTest(
      s"""import java.{util => utilRenamed}
         |import scala.collection.immutable.{IndexedSeq => IndexedSeqRenamed}
         |import scala.collection.mutable.{ArraySeq => ArraySeqRenamed}
         |import scala.util.{Random => RandomRenamed}
         |import java.util.{Deque => DequeRenamed}
         |
         |//noinspection ReferenceMustBePrefixed,ScalaUnusedExpression
         |${START}trait Example {
         |  null: utilRenamed.ArrayList[Int]
         |  null: IndexedSeqRenamed[Int]
         |  null: ArraySeqRenamed[Int]
         |  null: RandomRenamed
         |  null: DequeRenamed[Int]
         |}$END
         |""".stripMargin,
      "",
      """import java.util.{Deque => DequeRenamed}
        |import java.{util => utilRenamed}
        |import scala.collection.immutable.{IndexedSeq => IndexedSeqRenamed}
        |import scala.collection.mutable.{ArraySeq => ArraySeqRenamed}
        |import scala.util.{Random => RandomRenamed}
        |
        |trait Example {
        |  null: utilRenamed.ArrayList[Int]
        |  null: IndexedSeqRenamed[Int]
        |  null: ArraySeqRenamed[Int]
        |  null: RandomRenamed
        |  null: DequeRenamed[Int]
        |}""".stripMargin
    )
  }

  def testAddImportsOnPaste_MixWithRenamedImports(): Unit = {
    doTest(
      s"""import java.{util => utilRenamed}
         |import scala.collection.immutable.{IndexedSeq => IndexedSeqRenamed}
         |import scala.collection.mutable.{ArraySeq => ArraySeqRenamed}
         |import scala.util.Random
         |import java.util.Deque
         |
         |//noinspection ReferenceMustBePrefixed,ScalaUnusedExpression
         |${START}trait Example {
         |  null: utilRenamed.ArrayList[Int]
         |  null: IndexedSeqRenamed[Int]
         |  null: ArraySeqRenamed[Int]
         |  null: Random
         |  null: Deque[Int]
         |}$END
         |""".stripMargin,
      "",
      """|import java.util.Deque
         |import java.{util => utilRenamed}
         |import scala.collection.immutable.{IndexedSeq => IndexedSeqRenamed}
         |import scala.collection.mutable.{ArraySeq => ArraySeqRenamed}
         |import scala.util.Random
         |
         |trait Example {
         |  null: utilRenamed.ArrayList[Int]
         |  null: IndexedSeqRenamed[Int]
         |  null: ArraySeqRenamed[Int]
         |  null: Random
         |  null: Deque[Int]
         |}""".stripMargin
    )
  }
}

class CopyScalaToScala_WithAutoImportsTest_Scala3 extends CopyScalaToScala_WithAutoImportsTest_Scala2 {

  override protected def supportedIn(version: ScalaVersion) =
    version.isScala3

  override def testAddImportsOnPaste_RenamedImports(): Unit = {
    doTest(
      s"""import java.{util => utilRenamed}
         |import scala.collection.immutable.{IndexedSeq => IndexedSeqRenamed}
         |import scala.collection.mutable.{ArraySeq => ArraySeqRenamed}
         |import scala.util.{Random => RandomRenamed}
         |import java.util.{Deque => DequeRenamed}
         |
         |//noinspection ReferenceMustBePrefixed,ScalaUnusedExpression
         |${START}trait Example {
         |  null: utilRenamed.ArrayList[Int]
         |  null: IndexedSeqRenamed[Int]
         |  null: ArraySeqRenamed[Int]
         |  null: RandomRenamed
         |  null: DequeRenamed[Int]
         |}$END
         |""".stripMargin,
      "",
      """import java.util as utilRenamed
        |import java.util.Deque as DequeRenamed
        |import scala.collection.immutable.IndexedSeq as IndexedSeqRenamed
        |import scala.collection.mutable.ArraySeq as ArraySeqRenamed
        |import scala.util.Random as RandomRenamed
        |
        |trait Example {
        |  null: utilRenamed.ArrayList[Int]
        |  null: IndexedSeqRenamed[Int]
        |  null: ArraySeqRenamed[Int]
        |  null: RandomRenamed
        |  null: DequeRenamed[Int]
        |}""".stripMargin
    )
  }

  override def testAddImportsOnPaste_MixWithRenamedImports(): Unit = {
    doTest(
      s"""import java.{util => utilRenamed}
         |import scala.collection.immutable.{IndexedSeq => IndexedSeqRenamed}
         |import scala.collection.mutable.{ArraySeq => ArraySeqRenamed}
         |import scala.util.Random
         |import java.util.Deque
         |
         |//noinspection ReferenceMustBePrefixed,ScalaUnusedExpression
         |${START}trait Example {
         |  null: utilRenamed.ArrayList[Int]
         |  null: IndexedSeqRenamed[Int]
         |  null: ArraySeqRenamed[Int]
         |  null: Random
         |  null: Deque[Int]
         |}$END
         |""".stripMargin,
      "",
      """import java.util as utilRenamed
        |import java.util.Deque
        |import scala.collection.immutable.IndexedSeq as IndexedSeqRenamed
        |import scala.collection.mutable.ArraySeq as ArraySeqRenamed
        |import scala.util.Random
        |
        |trait Example {
        |  null: utilRenamed.ArrayList[Int]
        |  null: IndexedSeqRenamed[Int]
        |  null: ArraySeqRenamed[Int]
        |  null: Random
        |  null: Deque[Int]
        |}""".stripMargin
    )
  }

  def testAddImportsOnPaste_WithSamePathOriginalAndRenamed(): Unit = {
    doTest(
      s"""import scala.util.{Random => RandomRenamed}
         |import scala.util.Random
         |
         |${START}object Main {
         |  println(Random.nextInt())
         |  println(RandomRenamed.nextInt())
         |}$END
         |""".stripMargin,
      "",
      """import scala.util.Random
        |import scala.util.Random as RandomRenamed
        |
        |object Main {
        |  println(Random.nextInt())
        |  println(RandomRenamed.nextInt())
        |}""".stripMargin
    )
  }

  def testAddImportsOnPaste_WithSamePathOriginalAndRenamedMultipleTimes(): Unit = {
    doTest(
      s"""import scala.util.{Random => RandomRenamed1}
         |import scala.util.{Random => RandomRenamed2}
         |import scala.util.{Random => RandomRenamed3}
         |import scala.util.Random
         |
         |${START}object Main {
         |  println(Random.nextInt())
         |  println(RandomRenamed1.nextInt())
         |  println(RandomRenamed2.nextInt())
         |  println(RandomRenamed3.nextInt())
         |}$END
         |""".stripMargin,
      "",
      """import scala.util.Random
        |import scala.util.Random as RandomRenamed1
        |import scala.util.Random as RandomRenamed2
        |import scala.util.Random as RandomRenamed3
        |
        |object Main {
        |  println(Random.nextInt())
        |  println(RandomRenamed1.nextInt())
        |  println(RandomRenamed2.nextInt())
        |  println(RandomRenamed3.nextInt())
        |}""".stripMargin
    )
  }

  // SCL-22851
  def testAddImportsOnPaste_IntoBracedBlock(): Unit = {
    doTest(
      s"""import java.{util => a}
         |import scala.collection.immutable.{IndexedSeq => b}
         |import scala.collection.mutable.{ArraySeq => c}
         |import scala.util.{Random => d}
         |import java.util.{Deque => e}
         |
         |//noinspection ReferenceMustBePrefixed,ScalaUnusedExpression
         |${START}trait Example {
         |  null: a.ArrayList[Int]
         |  null: b[Int]
         |  null: c[Int]
         |  null: d
         |  null: e[Int]
         |}$END
         |""".stripMargin,
      s"""
         |object Outer {
         |  $CARET
         |}
         |""".stripMargin,
      """import java.util as a
        |import java.util.Deque as e
        |import scala.collection.immutable.IndexedSeq as b
        |import scala.collection.mutable.ArraySeq as c
        |import scala.util.Random as d
        |
        |object Outer {
        |  trait Example {
        |    null: a.ArrayList[Int]
        |    null: b[Int]
        |    null: c[Int]
        |    null: d
        |    null: e[Int]
        |  }
        |}
        |""".stripMargin
    )
  }


  def testAddImportsOnPaste_IntoIndentedBlock(): Unit = {
    doTest(
      s"""import java.{util => a}
         |import scala.collection.immutable.{IndexedSeq => b}
         |import scala.collection.mutable.{ArraySeq => c}
         |import scala.util.{Random => d}
         |import java.util.{Deque => e}
         |
         |//noinspection ReferenceMustBePrefixed,ScalaUnusedExpression
         |${START}trait Example {
         |  null: a.ArrayList[Int]
         |  null: b[Int]
         |  null: c[Int]
         |  null: d
         |  null: e[Int]
         |}$END
         |""".stripMargin,
      s"""
         |object Outer:
         |  $CARET
         |""".stripMargin,
      """import java.util as a
        |import java.util.Deque as e
        |import scala.collection.immutable.IndexedSeq as b
        |import scala.collection.mutable.ArraySeq as c
        |import scala.util.Random as d
        |
        |object Outer:
        |  trait Example {
        |    null: a.ArrayList[Int]
        |    null: b[Int]
        |    null: c[Int]
        |    null: d
        |    null: e[Int]
        |  }
        |""".stripMargin
    )
  }
}

class CopyScalaToScala_WithAutoImportsTest_Scala3_AskOnPaste extends CopyPasteTestBase {

  override protected def setUp(): Unit = {
    super.setUp()
    ScalaApplicationSettings.getInstance().ADD_IMPORTS_ON_PASTE = CodeInsightSettings.ASK
  }

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  private case class ExpectedImport(path: String, aliasName: Option[String] = None)

  private def doCopyPasteTestWithAskMode(
    sourceFileText: String,
    targetFileText: String,
    expectedFileText: String,
    expectedImports: Seq[ExpectedImport],
  ): Unit = {
    val promise: Promise[Seq[Associations.Binding]] = Promise()
    UiInterceptors.register(new UiInterceptors.UiInterceptor[RestoreReferencesDialog](classOf[RestoreReferencesDialog]) {
      override protected def doIntercept(dialog: RestoreReferencesDialog): Unit = {
        Disposer.register(getTestRootDisposable, dialog.getDisposable)
        promise.success(dialog.getBindings)

        dialog.performOKAction()
      }
    })

    doTest(
      sourceFileText,
      targetFileText,
      expectedFileText,
    )

    // we can wait for 0 seconds as we expect the promise to be finished at this moment
    val actualBindings = Await.result(promise.future, 0.second)
    val actualImports = actualBindings.map(b => ExpectedImport(b.path, b.aliasName))

    // NOTE: the rendering is tested in org.jetbrains.plugins.scala.conversion.copy.BindingCellRendererTest
    assertCollectionEquals(expectedImports, actualImports)
  }

  def testShowImportCandidatesAndAddImportsOnPaste(): Unit = {
    getFixture.addFileToProject("definitions.scala",
      """package org.example
        |
        |class MyClass
        |object MyObject
        |enum MyEnum {
        |  case MyCase
        |}
        |""".stripMargin
    )

    doCopyPasteTestWithAskMode(
      s"""import java.util
         |import scala.collection.mutable.ArraySeq
         |import scala.util.Random
         |import java.util.Deque
         |import org.example.{MyClass, MyEnum, MyObject}
         |
         |//noinspection ReferenceMustBePrefixed,ScalaUnusedExpression
         |${START}trait Example {
         |  null: util.ArrayList[Int]
         |  null: IndexedSeq[Int]
         |  null: ArraySeq[Int]
         |  null: Random
         |  null: Deque[Int]
         |
         |  println(new MyClass)
         |  println(new MyClass)
         |  println(MyObject)
         |  println(MyObject)
         |  println(MyEnum)
         |  println(MyEnum)
         |  println(MyEnum.MyCase)
         |  println(MyEnum.MyCase)
         |}$END
         |""".stripMargin,
      "",
      """import org.example.{MyClass, MyEnum, MyObject}
        |
        |import java.util
        |import java.util.Deque
        |import scala.collection.mutable.ArraySeq
        |import scala.util.Random
        |
        |trait Example {
        |  null: util.ArrayList[Int]
        |  null: IndexedSeq[Int]
        |  null: ArraySeq[Int]
        |  null: Random
        |  null: Deque[Int]
        |
        |  println(new MyClass)
        |  println(new MyClass)
        |  println(MyObject)
        |  println(MyObject)
        |  println(MyEnum)
        |  println(MyEnum)
        |  println(MyEnum.MyCase)
        |  println(MyEnum.MyCase)
        |}""".stripMargin,
      expectedImports = Seq(
        ExpectedImport("java.util"),
        ExpectedImport("java.util.Deque"),
        ExpectedImport("org.example.MyClass"),
        ExpectedImport("org.example.MyEnum"),
        ExpectedImport("org.example.MyObject"),
        ExpectedImport("scala.collection.mutable.ArraySeq"),
        ExpectedImport("scala.util.Random"),
      )
    )
  }
}