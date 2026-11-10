package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.Message.{Error, Warning}
import org.jetbrains.plugins.scala.util.CommonScalaRevertableChanges
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaBundle, ScalaVersion}

/**
 * Explicit arguments for context bound evidence must be passed with `using` since source version 3.4:
 * a warning in 3.4, an error since 3.5. The expected messages are the ones that the compiler reports,
 * except for 3.5-migration (see below).
 */
abstract class ExplicitContextBoundArgumentsAnnotatorTestBase extends ScalaHighlightingTestBase {

  protected def compilerOptions: Seq[String] = Seq.empty

  override protected def setUp(): Unit = {
    super.setUp()
    if (compilerOptions.nonEmpty) {
      CommonScalaRevertableChanges
        .withCompilerSettingsModified(myModule, s => s.copy(additionalCompilerOptions = s.additionalCompilerOptions ++ compilerOptions))
        .applyChange(this)
    }
  }

  protected val Message: String = ScalaBundle.message("explicit.arguments.for.context.bounds.require.using")

  /** Before 3.6, evidence that is not merged into a clause is `implicit` */
  protected val ImplicitEvidenceCode: String =
    """trait Show[A]
      |given Show[Int] = new Show[Int] {}
      |val sh: Show[Int] = summon[Show[Int]]
      |
      |def unnamed[A: Show]() = ()
      |def merged[A: Show](implicit x: Int) = ()
      |class C[A: Show]()
      |
      |def r1 = unnamed[Int]()(sh)
      |def r2 = merged[Int](sh, 1)
      |def r3 = new C[Int]()(sh)
      |def r4 = unnamed[Int]()(using sh)
      |def r5 = merged[Int](using sh, 1)
      |""".stripMargin
}

class ExplicitContextBoundArgumentsAnnotatorTest_3_3 extends ExplicitContextBoundArgumentsAnnotatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_3

  def testNoMessages(): Unit = assertNoMessages(ImplicitEvidenceCode)
}

class ExplicitContextBoundArgumentsAnnotatorTest_3_4 extends ExplicitContextBoundArgumentsAnnotatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_4

  def testWarnings(): Unit = assertMessages(ImplicitEvidenceCode,
    Warning("(sh)", Message),
    Warning("(sh, 1)", Message),
    Warning("(sh)", Message),
  )
}

class ExplicitContextBoundArgumentsAnnotatorTest_3_5 extends ExplicitContextBoundArgumentsAnnotatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_5

  def testErrors(): Unit = assertMessages(ImplicitEvidenceCode,
    Error("(sh)", Message),
    Error("(sh, 1)", Message),
    Error("(sh)", Message),
  )
}

/** A migration version is treated as its stable version (the compiler reports migration warnings instead) */
class ExplicitContextBoundArgumentsAnnotatorTest_3_9_Source3_5Migration extends ExplicitContextBoundArgumentsAnnotatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_9

  override protected def compilerOptions: Seq[String] = Seq("-source:3.5-migration")

  def testErrors(): Unit = assertMessages(ImplicitEvidenceCode,
    Error("(sh)", Message),
    Error("(sh, 1)", Message),
    Error("(sh)", Message),
  )
}

/** Since 3.6, evidence is `implicit` only when it is merged into an `implicit` clause */
class ExplicitContextBoundArgumentsAnnotatorTest_3_9 extends ExplicitContextBoundArgumentsAnnotatorTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_9

  def testMergedIntoImplicitClause(): Unit = assertMessages(
    """trait Show[A]
      |given Show[Int] = new Show[Int] {}
      |val sh: Show[Int] = summon[Show[Int]]
      |
      |def merged[A: Show](implicit x: Int) = ()
      |def named[A: Show as s](implicit x: Int) = ()
      |
      |def r1 = merged[Int](sh, 1)
      |def r2 = merged[Int](using sh, 1)
      |def r3 = named[Int](sh, 1)
      |""".stripMargin,
    Error("(sh, 1)", Message),
  )
}
