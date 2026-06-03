package org.jetbrains.plugins.scala.annotator.withLibraryDependencies

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingTestBase
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class DoobieHighlightingTest_Scala3 extends ScalaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == LatestScalaVersions.Scala_3

  override def librariesLoaders: Seq[LibraryLoader] =
    super.librariesLoaders :+ IvyManagedLoader(("org.tpolecat" %% "doobie-core" % "1.0.0-RC11").transitive())


  //SCL-24722
  def testSCL24722(): Unit = assertNoErrors(
    """import doobie._
      |import doobie.implicits._
      |
      |case class Country(code: String, name: String, population: Long)
      |
      |def find(n: String): ConnectionIO[Option[Country]] =
      |  sql"select code, name, population from country where name = $n".query[Country].option
      |""".stripMargin
  )
}
