package org.jetbrains.plugins.scala.findUsages.compilerReferences

import org.junit.Assert._
import org.junit.Test
import org.jetbrains.plugins.scala.findUsages.utils._

class ClassfileParserTest {
  @Test
  def testSimple(): Unit = {
    val parsed    = ClassfileParser.parse(loadClass[Simple])
    val classInfo = parsed.classInfo

    assertEquals(classInfo.internalName, internalName[Simple])
    assertEquals(classInfo.superClasses, Seq(internalName[Object]))
  }

  @Test
  def testWithSuperClasses(): Unit = {
    val parsed    = ClassfileParser.parse(loadClass[WithSuperClasses])
    val classInfo = parsed.classInfo

    assertEquals(classInfo.internalName, internalName[WithSuperClasses])
    assertEquals(
      classInfo.superClasses,
      Seq(internalName[Object], internalName[Simple], internalName[Serializable], internalName[Comparable[_]])
    )
  }
}

private class Simple {}

private abstract class WithSuperClasses extends Simple with Serializable with Comparable[Int]
