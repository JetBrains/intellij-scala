package org.jetbrains.plugins.scala.util.assertions

import org.jetbrains.plugins.scala.annotator.Message

trait MatcherAssertionsExt extends MatcherAssertions {

  def assertMessages(actual: List[Message])(expected: Message*): Unit =
    assertEqualsFailable(expected.mkString("\n"), actual.mkString("\n"))

  def assertMessagesSorted(actual: List[Message])(expected: Message*): Unit =
    assertMessages(actual.sorted)(expected.sorted: _*)

}
