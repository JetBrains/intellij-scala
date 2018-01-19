package org.jetbrains.plugins.scala.findUsages.compilerReferences

import java.util

import scala.collection.MapLike
import scala.collection.immutable.StringOps
import scala.reflect.ClassTag

import org.hamcrest.CoreMatchers._
import org.jetbrains.jps.backwardRefs.CompilerRef
import org.junit.Assert._
import org.junit.Test
import org.jetbrains.plugins.scala.findUsages.utils._

class ConstantPoolCompilerRefProviderTest {
  private[this] val writer = new MockCompilerReferenceWriter

  private def assertHasRefs[T: ClassTag](refs: String*): Unit = {
    val parsedClassfile = ClassfileParser.parse(loadClass[T])
    val refProvider     = new ConstantPoolCompilerRefProvider(parsedClassfile.cp, writer)

    val refNames = parsedClassfile.cp.getConstantPool.collect {
      case refProvider(ref) =>
        ref match {
          case cRef: CompilerRef.JavaCompilerClassRef => writer.getRefName(cRef)
          case mRef: CompilerRef.CompilerMember =>
            val className  = writer.getRefName(mRef.getOwner)
            val memberName = writer.getRefName(mRef)
            s"$className.$memberName"
        }
    }

    assertThat(util.Arrays.asList(refNames: _*), hasItems(refs: _*))
  }

  @Test
  def testClassBodyRefs(): Unit = assertHasRefs[WithRefs](
    memberOf[MapLike[_, _, _]]("contains"),
    memberOf[WithRefs]("s"),
    memberOf[Predef.type]("augmentString"),
    memberOf[Map.type]("apply"),
    memberOf[Predef.type]("println"),
    memberOf[StringOps]("toInt")
  )
}

private class HasImplicitVal { implicit val s: String = "foo" }

private class WithRefs extends HasImplicitVal {
  def foo(): Boolean = Map(42 -> "foo").contains(12)

  def bar(implicit s: String): Unit = println(s)

  bar

  def qux(s: String): Int = s.toInt
}
