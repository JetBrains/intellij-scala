package org.jetbrains.plugins.hocon

import scala.reflect.{ClassTag, classTag}

abstract class TestSuiteCompanion[T: ClassTag] {
  def suite: T =
    classTag[T].runtimeClass.asInstanceOf[Class[T]].newInstance
}
