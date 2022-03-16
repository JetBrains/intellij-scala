package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.stubs.{IndexSink, StubIndexKey}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGiven

final class ScGivenIndex extends ScStringStubIndexExtension[ScGiven] {
  override def getKey: StubIndexKey[String, ScGiven] = ScGivenIndex.indexKey
}

object ScGivenIndex extends ImplicitIndex[ScGiven] {
  override protected val indexKey: StubIndexKey[String, ScGiven] = ScalaIndexKeys.GIVEN_KEY

  def occurrences(sink: IndexSink, classNames: Array[String]): Unit =
    classNames.foreach(occurrence(sink, _))
}
