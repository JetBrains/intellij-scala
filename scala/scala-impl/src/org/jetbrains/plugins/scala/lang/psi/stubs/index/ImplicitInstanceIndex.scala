package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

final class ImplicitInstanceIndex extends ScStringStubIndexExtension[ScMember] {

  //noinspection TypeAnnotation
  override def getKey = ImplicitInstanceIndex.indexKey
}

object ImplicitInstanceIndex extends ImplicitIndex {

  //noinspection TypeAnnotation
  override protected val indexKey = ScalaIndexKeys.IMPLICIT_INSTANCE_KEY
}