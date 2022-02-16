package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.psi.stubs.IndexSink
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

final class ImplicitInstanceIndex extends ScStringStubIndexExtension[ScMember] {

  //noinspection TypeAnnotation
  override def getKey = ImplicitInstanceIndex.indexKey
}

object ImplicitInstanceIndex extends ImplicitIndex[ScMember] {

  final def occurrences(sink: IndexSink, names: Array[String]): Unit =
    names.foreach(occurrence(sink, _))

  //noinspection TypeAnnotation
  override protected val indexKey = ScalaIndexKeys.IMPLICIT_INSTANCE_KEY
}
