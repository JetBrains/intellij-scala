package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.File

import org.jetbrains.jps.incremental.scala.local.LazyCompiledClass
import org.jetbrains.plugin.scala.compilerReferences.{BuildData, Codec}
import org.jetbrains.plugin.scala.compilerReferences.Codec._
import org.junit.Assert._
import org.junit.Test

import scala.language.implicitConversions

class BuildDataCodecTest {
  private[this] def shouldRoundtrip(data: CodecEx*): Unit = data.foreach { d =>
    import d._
    val payload      = value.encode
    val deserialized = payload.decode[d.A]
    assertEquals("Deserialized data is not equal to initial data.", Some(value), deserialized)
  }
  
  private[this] sealed trait CodecEx {
    type A
    def value: A 
    implicit def ev: Codec[A]
  }
  
  private[this] final case class MkCodecEx[A0](value: A0)(implicit val ev: Codec[A0]) 
    extends CodecEx { type A = A0 }
  
  private[this] implicit def any2CodecEx[A: Codec](a: A): CodecEx = MkCodecEx(a)

  private[this] def compiledClass(out: String, source: String, name: String): CompiledClass =
    new LazyCompiledClass(new File(out), new File(source), name)

  @Test
  def testPrimitive(): Unit =
    shouldRoundtrip(42, false, 999999999999999L, "foobar")

  @Test
  def testCompiledClass(): Unit =
    shouldRoundtrip(
      compiledClass("foo.class", "fooSource.scala", "Foo")
    )

  @Test
  def testCollection(): Unit =
    shouldRoundtrip(
      Set(1, 2, 3),
      Set.empty[CompiledClass],
      Seq(
        compiledClass("foo1", "bar1", "baz1"),
        compiledClass("foo2", "bar2", "baz2"),
        compiledClass("foo3", "bar3", "baz3"),
        compiledClass("foo4", "bar4", "baz4")
      )
    )

  @Test
  def testBuildDataSimple(): Unit = shouldRoundtrip {
    val fooClass = compiledClass("output1", "source1", "class1")
    val barClass = compiledClass("output2", "source2", "class2")

    BuildData(
      123L,
      Set(fooClass, barClass),
      Set("foo", "bar", "baz"),
      Set("moduleFoo"),
      isRebuild = false
    )
  }

  @Test
  def testBuildDataComplex(): Unit = shouldRoundtrip {
    def randomString(): String                    = UUID.randomUUID().toString
    def generateRandomeClassData(): CompiledClass = compiledClass(randomString(), randomString(), randomString())

    val classes: Set[CompiledClass] = (1 to 10000).map(_ => generateRandomeClassData())(collection.breakOut)
    val sources: Set[String]        = (1 to 5000).map(_ => randomString())(collection.breakOut)
    val modules: Set[String]        = (1 to 100).map(_ => randomString())(collection.breakOut)
    BuildData(42L, classes, sources, modules, isCleanBuild = true)
  }
}
