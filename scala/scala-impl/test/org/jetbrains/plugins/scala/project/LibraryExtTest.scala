package org.jetbrains.plugins.scala.project

import junit.framework.TestCase
import org.junit.Assert.assertEquals

/**
 * NOTE: some "strange" examples in tests data are not because it's "DESIGNED" this way but because
 * it behaved this way for ages and I just wanted to cover current behaviour in tests.<br>
 * The designed behaviour can be reviewed if needed
 *
 * @see [[org.jetbrains.plugins.scala.project.VersionTest]]
 * @see [[org.jetbrains.plugins.scala.ScalaVersionTest]]
 */
//noinspection AssertBetweenInconvertibleTypes (workaround for SCL-22457)
class LibraryExtTest extends TestCase {
  def testGuessLibraryVersionFromName_NotALibraryName(): Unit = {
    assertEquals(None, LibraryExt.guessLibraryVersionFromName(""))
    assertEquals(None, LibraryExt.guessLibraryVersionFromName("  "))
    assertEquals(None, LibraryExt.guessLibraryVersionFromName("test"))
    assertEquals(None, LibraryExt.guessLibraryVersionFromName("1.22.3"))
  }

  def testGuessLibraryVersionFromName_JustVersion(): Unit = {
    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("-1.22.3"))
    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("----1.22.3"))
  }

  def testGuessLibraryVersionFromName(): Unit = {
    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("my-library-1.22.3"))

    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("my-library-1.22.3:jar"))

    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("sbt: my-library-1.22.3"))
    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("sbt: my-library-1.22.3:jar"))
    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("sbt: org.example:my-library-1.22.3:jar"))


    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("my library with spaces-1.22.3"))
    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("my library with spaces  -1.22.3"))
    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("sbt: org.example:my library with spaces  -1.22.3:jar"))
  }

  def testGuessLibraryVersionFromName_DashPrefix(): Unit = {
    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("library-1.22.3"))
  }

  def testGuessLibraryVersionFromName_ColonPrefix(): Unit = {
    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("library:1.22.3"))
  }

  def testGuessLibraryVersionFromName_UnderscorePrefix(): Unit = {
    assertEquals(Some("1.22.3"), LibraryExt.guessLibraryVersionFromName("library_1.22.3"))
  }

  //SCL-22444
  def testGuessLibraryVersionFromName_SCL_22444(): Unit = {
    assertEquals(Some("2.13.13"), LibraryExt.guessLibraryVersionFromName("scala-sdk_2.13.13:jar"))
    assertEquals(Some("2.13.13"), LibraryExt.guessLibraryVersionFromName("scala-sdk__2.13.13:jar"))
    assertEquals(Some("2.13.13"), LibraryExt.guessLibraryVersionFromName("sbt: scala-sdk__2.13.13:jar"))

    assertEquals(Some("2.12.15-bin-db-2-fd41f6b"), LibraryExt.guessLibraryVersionFromName("org.scala-lang__scala-library_2.12.15-bin-db-2-fd41f6b"))
    assertEquals(Some("2.12.15-bin-db-2-fd41f6b"), LibraryExt.guessLibraryVersionFromName("org.scala-lang__scala-library__2.12.15-bin-db-2-fd41f6b"))
    assertEquals(Some("2.12.15-bin-db-2-fd41f6b"), LibraryExt.guessLibraryVersionFromName("sbt: org.scala-lang__scala-library__2.12.15-bin-db-2-fd41f6b"))
    assertEquals(Some("2.12.15-bin-db-2-fd41f6b"), LibraryExt.guessLibraryVersionFromName("sbt: org.scala-lang__scala-library__2.12.15-bin-db-2-fd41f6b:jar"))
  }

  def testGuessLibraryVersionFromName_VersionWithSuffix(): Unit = {
    assertEquals(Some("2.12.18"), LibraryExt.guessLibraryVersionFromName("sbt: scala-sdk-2.12.18"))
    assertEquals(Some("2.12.18-bin-db-2-fd41f6b"), LibraryExt.guessLibraryVersionFromName("sbt: scala-sdk-2.12.18-bin-db-2-fd41f6b"))

    assertEquals(Some("2.12.18"), LibraryExt.guessLibraryVersionFromName("sbt: org.scala-lang:scala-library:2.12.18:jar"))
    assertEquals(Some("2.12.18-bin-db-2-fd41f6b"), LibraryExt.guessLibraryVersionFromName("sbt: org.scala-lang:scala-library:2.12.18-bin-db-2-fd41f6b:jar"))

    assertEquals(Some("2.12.18"), LibraryExt.guessLibraryVersionFromName("sbt: org.scala-lang:scala-library:2.12.18   :   jar"))
    assertEquals(Some("2.12.18-bin-db-2-fd41f6b"), LibraryExt.guessLibraryVersionFromName("sbt: org.scala-lang:scala-library:2.12.18-bin-db-2-fd41f6b   :   jar"))
  }

  def testGuessLibraryVersionFromName_BadPrefixBeforeVersion(): Unit = {
    assertEquals(None, LibraryExt.guessLibraryVersionFromName("my library 1.22.3"))
    assertEquals(None, LibraryExt.guessLibraryVersionFromName("my-library 1.22.3"))
    assertEquals(None, LibraryExt.guessLibraryVersionFromName("my library with spaces  - 1.22.3"))
  }
}
