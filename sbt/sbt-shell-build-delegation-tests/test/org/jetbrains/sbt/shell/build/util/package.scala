package org.jetbrains.sbt.shell.build

import java.util
import scala.jdk.CollectionConverters.IteratorHasAsScala

package object util {
  def nonEmptyEntries(values: util.Collection[String]): Seq[String] =
    values.iterator().asScala.filter(_.nonEmpty).toSeq
}
