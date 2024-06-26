package org.jetbrains.plugins.scala.lang.actions.editor.copy

import com.intellij.codeInsight.CodeInsightSettings
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

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
         |import scala.collection.immutable.IndexedSeq
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
}