package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.io.InputStream

import scala.collection.MapLike
import scala.collection.immutable.StringOps
import scala.reflect.ClassTag

import org.junit.Assert._
import org.junit.Test
import org.hamcrest.CoreMatchers._

class ClassfileParserTest {
  private def loadClass[A](implicit tag: ClassTag[A]): InputStream = {
    val path = fqn[A].replaceAll("\\.", "/") + ".class"
    getClass.getClassLoader.getResourceAsStream(path)
  }

  private def fqn[A](implicit tag: ClassTag[A]): String = tag.runtimeClass.getName

  private def methodRefOf[A: ClassTag](name: String, line: Int, args: Int): MethodReference =
    MethodReference(fqn[A], name, line, args)

  private def fieldRefOf[A: ClassTag](name: String, line: Int): FieldReference =
    FieldReference(fqn[A], name, line)

  private def doTest[A](body: ParsedClassfile => Unit)(implicit tag: ClassTag[A]): Unit = {
    val parsed = ClassfileParser.parse(loadClass[A])
    assertEquals(fqn[A], parsed.classInfo.fqn)
    body(parsed)
  }

  @Test
  def testSimple(): Unit = doTest[Simple] { parsed =>
    val classInfo = parsed.classInfo
    assertEquals(Set(fqn[Object]), classInfo.superClasses)
  }

  @Test
  def testWithSuperClasses(): Unit = doTest[WithSuperClasses] { parsed =>
    val classInfo = parsed.classInfo

    assertEquals(
      Set(fqn[Simple], fqn[Serializable], fqn[Comparable[_]]),
      classInfo.superClasses
    )
  }

  @Test
  def testWithRefs(): Unit = doTest[WithRefs] { parsed =>
    val classInfo = parsed.classInfo

    assertEquals(
      Set(fqn[HasImplicitVal]),
      classInfo.superClasses
    )

    val expectedRefs: Seq[MemberReference] = Seq(
      methodRefOf[Predef.type]("Map", 83, 0),
      fieldRefOf[WithRefs]("noGetter", 91),
      methodRefOf[MapLike[_, _, _]]("contains", 83, 1),
      methodRefOf[WithRefs]("s", 87, 0),
      methodRefOf[Predef.type]("augmentString", 93, 1),
      methodRefOf[Map.type]("apply", 83, 1),
      methodRefOf[Predef.type]("println", 85, 1),
      methodRefOf[StringOps]("toInt", 93, 0)
    )

    assertThat(
      java.util.Arrays.asList(parsed.refs: _*),
      hasItems(expectedRefs: _*)
    )
  }
}

private class Simple {}

private abstract class WithSuperClasses extends Simple with Serializable with Comparable[Int]

private class HasImplicitVal { implicit val s: String = "foo" }

private class WithRefs extends HasImplicitVal {
  def foo(): Boolean = Map(42 -> "foo").contains(12)

  def bar(implicit s: String): Unit = println(s)

  bar

  private[this] implicit val noGetter: Int = 42
  def baz(implicit i: Int): Int = i * 2
  baz
  
  def qux(s: String): Int = s.toInt
}
