package org.jetbrains.plugins.scala.util.assertions

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.Message
import org.jetbrains.plugins.scala.base.FailableTest
import org.junit.Assert

import scala.reflect.ClassTag

trait MatcherAssertions extends FailableTest {

  def assertNothing[T](actual: Option[T]): Unit =
    assertMatches(actual) {
      case Nil =>
    }

  def assertMatches[T](actual: Option[T])(pattern: PartialFunction[T, Unit]): Unit =
    actual match {
      case Some(value) =>
        def message = if (shouldPass) {
          val actualValueFancy = value match {
            case seq: Seq[_] => seq.mkString(s"${seq.getClass.getSimpleName}(\n  ", ",\n  ", "\n)")
            case v                  => v.toString
          }
          "actual: " + actualValueFancy
        } else {
          failingPassed
        }
        Assert.assertTrue(message, shouldPass == pattern.isDefinedAt(value))
      case None => Assert.assertFalse(shouldPass)
    }

  def assertNothing[T](actual: T): Unit =
    assertNothing(Some(actual))

  def assertMatches[T](actual: T)(pattern: PartialFunction[T, Unit]): Unit =
    assertMatches(Some(actual))(pattern)

  def assertMessages(actual: List[Message])(expected: Message*): Unit =
    assertEqualsFailable(expected.mkString("\n"), actual.mkString("\n"))

  def assertMessagesSorted(actual: List[Message])(expected: Message*): Unit =
    assertMessages(actual.sorted)(expected.sorted: _*)

  def assertIsA[T](obj: Object)(implicit classTag: ClassTag[T]): T =
    if (classTag.runtimeClass.isInstance(obj)) {
      obj.asInstanceOf[T]
    } else {
      Assert.fail(s"wrong object class\nexpected ${classTag.runtimeClass.getName}\nactual:${obj.getClass.getName}").asInstanceOf[Nothing]
    }

  case class ContainsPattern(fragment: String) {
    def unapply(s: String): Boolean = s.contains(fragment)
  }

  case class BundleMessagePattern(@Nls message: String) {
    def unapply(text: String): Boolean = text == message
  }
}

object MatcherAssertions extends MatcherAssertions