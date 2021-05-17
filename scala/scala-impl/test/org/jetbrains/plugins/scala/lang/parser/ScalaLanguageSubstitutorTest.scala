package org.jetbrains.plugins.scala.lang.parser

import junit.framework.TestCase
import org.junit.Assert.{assertFalse, assertTrue}

class ScalaLanguageSubstitutorTest extends TestCase {

  import ScalaLanguageSubstitutor.looksLikeScala3LibJar

  private def toPathInJar(jarName: String): String = {
    s"/c/root/$jarName!/my/file.scala"
  }

  private def assertIsRecognised(jarName: String): Unit = {
    val path = toPathInJar(jarName)
    assertTrue(s"Path isn't recognised as a scala3 library jar: $jarName", looksLikeScala3LibJar(path))
  }

  private def assertNotRecognised(jarName: String): Unit = {
    val path = toPathInJar(jarName)
    assertFalse(s"Path shouldn't be recognised as a scala3 library jar: $jarName", looksLikeScala3LibJar(path))
  }

  def testLooksLikeScala3LibJar_CompilerJars(): Unit ={
    assertIsRecognised("scala3-library_3-3.0.0-sources.jar")
    assertIsRecognised("scala3-compiler_3-3.0.1-sources.jar")
  }

  def testLooksLikeScala3LibJar_CompilerJars_DevVersions(): Unit ={
    assertIsRecognised("scala3-library_3-3.0.0-RC1-sources.jar")
    assertIsRecognised("scala3-compiler_3-3.0.1-RC1-bin-20210504-d81b0e5-NIGHTLY-sources.jar")
    assertIsRecognised("scala3-compiler_3-3.0.1-M1-sources.jar")
  }

  def testLooksLikeScala3LibJar_CommonLibraries(): Unit ={
    assertIsRecognised("scalatest-core_3-3.2.9-sources.jar")
    assertIsRecognised("airframe-surface_3-21.5.4-sources.jar")
  }

  def testLooksLikeScala3LibJar_CommonLibraries_DevVersions(): Unit ={
    assertIsRecognised("scalatest-core_3-3.2.9-RC1-sources.jar")
    assertIsRecognised("airframe-surface_3-21.5.4-qwef-1234-sources.jar")
  }

  // includes non-realistic versions
  def testDoesNotLooksLikeScala3LibJar(): Unit ={
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
}
