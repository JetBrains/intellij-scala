package org.jetbrains.plugins.scala.util.matchers

import org.hamcrest.{BaseMatcher, Description}

/**
 * Base matcher for using with [[org.junit.Assert.assertThat]] or [[org.junit.Assume.assumeThat]].
 * Please use extending of this class instead of [[org.hamcrest.BaseMatcher]].
 *
 * @tparam V type of the actual and expected value
 */
abstract class ScalaBaseMatcher[V]
  extends BaseMatcher[V] {

  /**
   * Checks if actual value is matches matcher.
   */
  protected def valueMatches(actualValue: V): Boolean

  /**
   * Text description of the matcher.
   */
  protected def description: String

  final override def matches(item: Any): Boolean =
    valueMatches(item.asInstanceOf[V])

  final override def describeTo(desc: Description): Unit =
    desc.appendText(description)
}
