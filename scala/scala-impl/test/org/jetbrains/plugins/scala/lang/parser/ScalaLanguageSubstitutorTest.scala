package org.jetbrains.plugins.scala.lang.parser

import junit.framework.TestCase
import org.junit.Assert.{assertFalse, assertTrue}

class ScalaLanguageSubstitutorTest extends TestCase {

  import ScalaLanguageSubstitutor.looksLikeScala3LibSourcesJar

  private def toPathInJar(jarName: String): String = {
    s"/c/root/$jarName!/my/file.scala"
  }

  private def assertIsRecognised(jarName: String): Unit = {
    val path = toPathInJar(jarName)
    assertTrue(s"Path isn't recognised as a scala3 library jar: $jarName", looksLikeScala3LibSourcesJar(path))
  }

  private def assertNotRecognised(jarName: String): Unit = {
    val path = toPathInJar(jarName)
    assertNotRecognisedPath(path)
  }

  private def assertNotRecognisedPath(path: String): Unit = {
    assertFalse(s"Path shouldn't be recognised as a scala3 library jar: $path", looksLikeScala3LibSourcesJar(path))
  }

  def testLooksLikeScala3LibJar_CompilerJars(): Unit = {
    assertIsRecognised("scala3-library_3-3.0.0-sources.jar")
    assertIsRecognised("scala3-compiler_3-3.0.1-sources.jar")
  }

  def testLooksLikeScala3LibJar_CompilerJars_DevVersions(): Unit = {
    assertIsRecognised("scala3-library_3-3.0.0-RC1-sources.jar")
    assertIsRecognised("scala3-compiler_3-3.0.1-RC1-bin-20210504-d81b0e5-NIGHTLY-sources.jar")
    assertIsRecognised("scala3-compiler_3-3.0.1-M1-sources.jar")
  }

  def testLooksLikeScala3LibJar_CommonLibraries(): Unit = {
    assertIsRecognised("scalatest-core_3-3.2.9-sources.jar")
    assertIsRecognised("airframe-surface_3-21.5.4-sources.jar")
  }

  def testLooksLikeScala3LibJar_CommonLibraries_DevVersions(): Unit = {
    assertIsRecognised("scalatest-core_3-3.2.9-RC1-sources.jar")
    assertIsRecognised("airframe-surface_3-21.5.4-qwef-1234-sources.jar")
    assertIsRecognised("quill-sql_3-3.16.4-Beta2.6-sources.jar")
  }

  def testLooksLikeScala3LibJar_DevVersions(): Unit = {
    assertIsRecognised("library-name_3-1.0.0-alpha-sources.jar")
    assertIsRecognised("library-name_3-1.0.0-alpha.1-sources.jar")
    assertIsRecognised("library-name_3-1.0.0-0.3.7-sources.jar")
    assertIsRecognised("library-name_3-1.0.0-x.7.z.92-sources.jar")
    assertIsRecognised("library-name_3-1.0.0-x-y-z.--sources.jar")
  }

  def testLooksLikeScala3LibJar_DevVersions_WithBuildData(): Unit = {
    assertIsRecognised("library-name_3-1.0.0-alpha+001-sources.jar")
    assertIsRecognised("library-name_3-1.0.0+20130313144700-sources.jar")
    assertIsRecognised("library-name_3-1.0.0-beta+exp.sha.5114f85-sources.jar")
    assertIsRecognised("library-name_3-1.0.0+21AF26D3--117B344092BD-sources.jar")

    assertIsRecognised("library-name_3-1.0.0-alpha+21AF26D3--117B344092BD-sources.jar")
    assertIsRecognised("library-name_3-1.0.0-alpha.1+21AF26D3--117B344092BD-sources.jar")
    assertIsRecognised("library-name_3-1.0.0-0.3.7+21AF26D3--117B344092BD-sources.jar")
    assertIsRecognised("library-name_3-1.0.0-x.7.z.92+21AF26D3--117B344092BD-sources.jar")
  }

  def testDoesNotLooksLikeScala3LibJar_No_3SuffixAfterLibraryNameMainPart(): Unit = {
    //do not have "_3" suffix after library name main part
    assertNotRecognised("scala-library-3.0.0-sources.jar")
    assertNotRecognised("scala3-library-3.0.0-sources.jar")
    assertNotRecognised("scala3-library_2.12-3.0.0-sources.jar")
    assertNotRecognised("scala3-library_2.13-3.0.0-sources.jar")
    assertNotRecognised("scala3-library_2.13-2.13.5-sources.jar")

    assertNotRecognised("scala3-library_2.13-3.0.0-RC1-sources.jar")
    assertNotRecognised("scala3-compiler-3.0.1-RC1-bin-20210504-d81b0e5-NIGHTLY-sources.jar")

    assertNotRecognised("scalatest-core_2.12-3.2.9-sources.jar")
    assertNotRecognised("scalatest-core_2.13-3.2.9-sources.jar")
  }

  def testDoesNotLooksLikeScala3LibJar_ContainsDotInsteadOfHyphenAfterPatchVersionPart(): Unit = {
    //contains dot `.` instead of dash/hyphen `-` after PATCH version (just before Beta)
    assertNotRecognised("quill-sql_3-3.16.4.Beta2.6-sources.jar")
  }

  def testDoesNotLooksLikeScala3LibJar_DoesntContainSourcesJarSuffix(): Unit = {
    assertNotRecognised("library-name_3-1.0.0.jar")
    assertNotRecognised("library-name_3-1.0.0-sources")
    assertNotRecognised("library-name_3-1.0.0-sources.zip")
  }

  def testDoesNotLooksLikeScala3LibJar_TestFullPath(): Unit = {
    assertNotRecognisedPath("")
    assertNotRecognisedPath("/")
    assertNotRecognisedPath("///")

    assertNotRecognisedPath("/library-name/")
    assertNotRecognisedPath("/library-name!")
    assertNotRecognisedPath("/library-name!/")
    assertNotRecognisedPath("/library-name.jar!/")
    assertNotRecognisedPath("/library-name_2.13.jar!/")
    assertNotRecognisedPath("/library-name_2.13-1.0.0.jar!/")
    assertNotRecognisedPath("/library-name_2.13-sources.jar!/")
    assertNotRecognisedPath("/library-name_2.13-1.0.0-sources.jar!/")

    assertNotRecognisedPath("/library-name_3.jar!/")
    assertNotRecognisedPath("/library-name_3-sources/")
    assertNotRecognisedPath("/library-name_3-sources.zip!/")
    assertNotRecognisedPath("/library-name_3-sources.jar!/")
    assertNotRecognisedPath("/library-name_3--sources.jar!/")
  }
}
