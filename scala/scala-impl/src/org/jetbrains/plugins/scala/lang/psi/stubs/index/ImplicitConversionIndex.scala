package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndexKey.createIndexKey
import com.intellij.psi.stubs.{IndexSink, StubIndexKey}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.project.ProjectContext

/**
 * @author Alexander Podkhalyuzin
 */
class ImplicitConversionIndex extends ScStringStubIndexExtension[ScMember] {
  override def getKey: StubIndexKey[String, ScMember] = ImplicitConversionIndex.indexKey
}
object ImplicitConversionIndex extends StubIndexExt[String, ScMember] {
  //only implicit classes and implicit conversion defs are indexed
  //there is also a case when implicit conversion is provided by an implicit val with function type, but I think it is too exotic to support
  //no meaningful keys are provided, we just need to be able to enumerate all implicit conversions in a scope
  val indexKey: StubIndexKey[String, ScMember] = createIndexKey("sc.implicit.conversion")

  private val dummyStringKey: String = "implicit_conversion"

  def allElements(scope: GlobalSearchScope)
                 (implicit context: ProjectContext): Iterable[ScMember] =
    elements(dummyStringKey, scope, classOf[ScMember])

  def occurrence(sink: IndexSink): Unit = occurrence(sink, dummyStringKey)
}