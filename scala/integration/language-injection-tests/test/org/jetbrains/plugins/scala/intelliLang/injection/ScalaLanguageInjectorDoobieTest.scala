package org.jetbrains.plugins.scala.intelliLang.injection

import com.intellij.lang.LanguageParserDefinitions
import org.intellij.lang.annotations.{Language as LanguageAnnotation}
import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.junit.Assert.assertNotNull

import scala.jdk.CollectionConverters.IterableHasAsScala

// SCL-24942
/**
 * Tests SQL language injection for real Doobie constructors from `doobie-core` 1.0.0-RC13.
 *
 * Tested Doobie APIs:
 * - [[https://github.com/typelevel/doobie/blob/v1.0.0-RC13/modules/core/src/main/scala/doobie/util/update.scala#L269 org.typelevel.doobie.util.update.Update.apply]]
 * - [[https://github.com/typelevel/doobie/blob/v1.0.0-RC13/modules/core/src/main/scala/doobie/util/update.scala#L368 org.typelevel.doobie.util.update.Update0.apply]]
 * - [[https://github.com/typelevel/doobie/blob/v1.0.0-RC13/modules/core/src/main/scala/doobie/util/query.scala#L294 org.typelevel.doobie.util.query.Query.apply]]
 * - [[https://github.com/typelevel/doobie/blob/v1.0.0-RC13/modules/core/src/main/scala/doobie/util/query.scala#L456 org.typelevel.doobie.util.query.Query0.apply]]
 */
abstract class ScalaLanguageInjectorDoobieTestBase extends ScalaLanguageInjectionTestBase {

  private val InsertSql = "insert into person (name, age) values (?, ?)"
  private val SelectSql = "select name, age from person where age > ?"

  private val DoobieLatestVersion = "1.0.0-RC13"

  private val SqlLanguageId = "SQL"
  private val InjectedSqlLanguageId = "GenericSQL"

  override protected def setUp(): Unit = {
    super.setUp()

    //If we don't disable "caresAboutInjection" flag, the original Scala file which contains string literal will be lost
    //CodeInsightTestFixtureImpl.setupEditorForInjectedLanguage will be called and override `myFile` with the injected file
    //As an alternative we manually set up editor for the injected fragment in `doTypingTest`
    this.myFixture.setCaresAboutInjection(false)

    assertSqlLanguageExistsAndRegistered()
  }

  private def assertSqlLanguageExistsAndRegistered(): Unit = {
    // We need to access the language instance to make sure that it's initialized and registered
    // Otherwise, language injection logic won't find the language with id "SQL".
    com.intellij.sql.psi.SqlLanguage.INSTANCE

    val sqlLanguageFound = com.intellij.lang.Language.findLanguageByID(SqlLanguageId)
    assertNotNull("SQL language not found", sqlLanguageFound)

    // For language injection it's important that the language is also registered
    // (see org.intellij.plugins.intelliLang.inject.InjectedLanguage.findLanguageById)
    val registeredLanguages = com.intellij.lang.Language.getRegisteredLanguages
    val sqlRegisteredLanguageFound = registeredLanguages.asScala.find(_.getID == SqlLanguageId)
    assertNotNull("SQL language not registered", sqlRegisteredLanguageFound)

    val parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(sqlLanguageFound)
    assertNotNull("SQL language parser definition not found", parserDefinition)
  }

  override protected def additionalLibraries: Seq[LibraryLoader] =
    Seq(IvyManagedLoader(("org.typelevel" %% "doobie-core" % DoobieLatestVersion).transitive()))

  def testUpdateApply_WithExplicitApplyAndTypeArgument(): Unit =
    doDoobieSqlInjectionTest(
      s"""Update.apply[Unit]("$CARET$InsertSql")""",
      InsertSql
    )

  def testUpdateApply_WithSyntacticApplyAndTypeArgument(): Unit =
    doDoobieSqlInjectionTest(
      s"""Update[Unit]("$CARET$InsertSql")""",
      InsertSql
    )

  def testUpdateApply_WithExplicitApplyAndInferredTypeArgument(): Unit =
    doDoobieSqlInjectionTest(
      s"""val update: Update[Unit] = Update.apply("$CARET$InsertSql")""",
      InsertSql
    )

  def testUpdateApply_WithSyntacticApplyAndInferredTypeArgument(): Unit =
    doDoobieSqlInjectionTest(
      s"""val update: Update[Unit] = Update("$CARET$InsertSql")""",
      InsertSql
    )

  def testUpdateApply_WithNamedSqlArgument(): Unit =
    doDoobieSqlInjectionTest(
      s"""Update[Unit](sql = "$CARET$InsertSql")""",
      InsertSql
    )

  def testUpdateApply_WithFullArgumentList(): Unit =
    doDoobieSqlInjectionTest(
      s"""Update[Unit]("$CARET$InsertSql", None, "insert-person")""",
      InsertSql
    )

  def testUpdate0Apply_WithExplicitApply(): Unit =
    doDoobieSqlInjectionTest(
      s"""Update0.apply("$CARET$InsertSql", None)""",
      InsertSql
    )

  def testUpdate0Apply_WithSyntacticApply(): Unit =
    doDoobieSqlInjectionTest(
      s"""Update0("$CARET$InsertSql", None)""",
      InsertSql
    )

  def testUpdate0Apply_WithNamedSqlArgument(): Unit =
    doDoobieSqlInjectionTest(
      s"""Update0(sql0 = "$CARET$InsertSql", pos0 = None)""",
      InsertSql
    )

  def testUpdate0Apply_WithExpectedType(): Unit =
    doDoobieSqlInjectionTest(
      s"""val update: Update0 = Update0("$CARET$InsertSql", None)""",
      InsertSql
    )

  def testQueryApply_WithExplicitApplyAndTypeArguments(): Unit =
    doDoobieSqlInjectionTest(
      s"""Query.apply[Unit, Int]("$CARET$SelectSql")""",
      SelectSql
    )

  def testQueryApply_WithSyntacticApplyAndTypeArguments(): Unit =
    doDoobieSqlInjectionTest(
      s"""Query[Unit, Int]("$CARET$SelectSql")""",
      SelectSql
    )

  def testQueryApply_WithExplicitApplyAndInferredTypeArguments(): Unit =
    doDoobieSqlInjectionTest(
      s"""val query: Query[Unit, Int] = Query.apply("$CARET$SelectSql")""",
      SelectSql
    )

  def testQueryApply_WithSyntacticApplyAndInferredTypeArguments(): Unit =
    doDoobieSqlInjectionTest(
      s"""val query: Query[Unit, Int] = Query("$CARET$SelectSql")""",
      SelectSql
    )

  def testQueryApply_WithNamedSqlArgument(): Unit =
    doDoobieSqlInjectionTest(
      s"""Query[Unit, Int](sql = "$CARET$SelectSql")""",
      SelectSql
    )

  def testQueryApply_WithFullArgumentList(): Unit =
    doDoobieSqlInjectionTest(
      s"""Query[Unit, Int]("$CARET$SelectSql", None, "select-person")""",
      SelectSql
    )

  def testQuery0Apply_WithExplicitApplyAndTypeArgument(): Unit =
    doDoobieSqlInjectionTest(
      s"""Query0.apply[Int]("$CARET$SelectSql")""",
      SelectSql
    )

  def testQuery0Apply_WithSyntacticApplyAndTypeArgument(): Unit =
    doDoobieSqlInjectionTest(
      s"""Query0[Int]("$CARET$SelectSql")""",
      SelectSql
    )

  def testQuery0Apply_WithExplicitApplyAndInferredTypeArgument(): Unit =
    doDoobieSqlInjectionTest(
      s"""val query: Query0[Int] = Query0.apply("$CARET$SelectSql")""",
      SelectSql
    )

  def testQuery0Apply_WithSyntacticApplyAndInferredTypeArgument(): Unit =
    doDoobieSqlInjectionTest(
      s"""val query: Query0[Int] = Query0("$CARET$SelectSql")""",
      SelectSql
    )

  def testQuery0Apply_WithNamedSqlArgument(): Unit =
    doDoobieSqlInjectionTest(
      s"""Query0[Int](sql = "$CARET$SelectSql")""",
      SelectSql
    )

  def testQuery0Apply_WithFullArgumentList(): Unit =
    doDoobieSqlInjectionTest(
      s"""Query0[Int]("$CARET$SelectSql", None, "select-person")""",
      SelectSql
    )

  private def doDoobieSqlInjectionTest(@LanguageAnnotation("Scala") callExpression: String, expectedSql: String): Unit = {
    scalaInjectionTestFixture.doTest(
      InjectedSqlLanguageId,
      s"""// Just add all the imports just in case to avoid doing this in every concrete test case
         |import org.typelevel.doobie.implicits._
         |import org.typelevel.doobie.util.update.{Update, Update0}
         |import org.typelevel.doobie.util.query.{Query, Query0}
         |
         |object Usage {
         |  $callExpression
         |}
         |""".stripMargin,
      expectedSql,
    )
  }
}

class ScalaLanguageInjectorDoobieTest extends ScalaLanguageInjectorDoobieTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13
}

class ScalaLanguageInjectorDoobieTest_Scala3 extends ScalaLanguageInjectorDoobieTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3
}
