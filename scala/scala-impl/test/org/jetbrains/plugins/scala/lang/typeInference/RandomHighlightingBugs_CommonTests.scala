package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestLike
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

trait RandomHighlightingBugs_CommonTests extends ScalaLightCodeInsightFixtureTestCase with ScalaHighlightingTestLike {

  object SCL24453 {
    val CommonDefinitions: String =
      """trait Profile {
        |  type Database
        |  val db: Database = ???
        |}
        |
        |trait JdbcProfile extends Profile {
        |  type Database = JdbcDatabase
        |
        |  class JdbcDatabase {
        |    def jdbcFoo: Int = ???
        |  }
        |}
        |
        |trait DatabaseConfig[P <: Profile] {
        |  val profile: P
        |
        |  def db: profile.Database = profile.db
        |}
        |
        |trait Example[P <: Profile] {
        |  val dbConfig: DatabaseConfig[P] = ???
        |  lazy val dbConfigLazy: DatabaseConfig[P] = ???
        |
        |  val db: dbConfig.profile.Database = dbConfig.db
        |  val dbLazy = dbConfigLazy.db
        |}
        |""".stripMargin

    val CodeExample1: String =
      """object UsageMixing extends Example[JdbcProfile] {
        |  val db11: dbConfig.profile.Database = db
        |  val db12: JdbcProfile#JdbcDatabase = db
        |
        |  // This is an error in Scala 3 but it shouldn't (we can't use dbConfigLazy here)
        |  // but it shouldn't effect the further inference.
        |  // We emulate the types in the decompiled class
        |  val db21: dbConfigLazy.profile.Database = dbLazy
        |  val db22: JdbcProfile#JdbcDatabase = dbLazy
        |
        |  db11.jdbcFoo
        |  db12.jdbcFoo
        |  db21.jdbcFoo
        |  db22.jdbcFoo
        |}
        |
        |""".stripMargin

    val CodeExample2: String =
      """object UsageObject2 {
        |  val example: Example[JdbcProfile] = ???
        |
        |  // This is an error in Scala 3 but it shouldn't (we can't use dbConfigLazy here)
        |  // but it shouldn't effect the further inference.
        |  // We emulate the types in the decompiled class
        |  val db1: UsageObject2.example.dbConfig.profile.Database = example.db
        |  val db2: UsageObject2.example.dbConfigLazy.profile.Database = example.dbLazy
        |
        |  db1.jdbcFoo
        |  db2.jdbcFoo
        |}
        |""".stripMargin
  }

  def test_SCL24453_1(): Unit
  def test_SCL24453_2(): Unit

  //SCL-24679, SCL-24453
  def testAllowEffectivelyFinalLazyValsInStableReferences(): Unit = checkTextHasNoErrors(
    """import scala.language.implicitConversions
      |import MyObject.myLazyVal1
      |
      |trait MyTrait {
      |  val value: String = ???
      |  lazy val lazyValue: String = ???
      |  type MyType1
      |  type MyType2 = String
      |}
      |
      |object MyObject {
      |  lazy val myLazyVal1: MyTrait = ???
      |  ??? : myLazyVal1.type
      |  ??? : myLazyVal1.value.type
      |}
      |""".stripMargin
  )
}