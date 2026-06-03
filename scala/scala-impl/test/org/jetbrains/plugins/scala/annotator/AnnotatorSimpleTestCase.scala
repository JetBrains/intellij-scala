package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.base.{SharedTestProjectToken, SimpleTestCase}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
abstract class AnnotatorSimpleTestCase extends SimpleTestCase {

  override protected def sharedProjectToken: SharedTestProjectToken =
    SharedTestProjectToken(classOf[AnnotatorSimpleTestCase])
}
