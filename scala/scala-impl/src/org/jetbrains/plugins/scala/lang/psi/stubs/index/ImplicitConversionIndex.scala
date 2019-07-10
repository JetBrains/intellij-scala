package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndexKey.createIndexKey
import com.intellij.psi.stubs.{IndexSink, StubIndexKey}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
 * @author Alexander Podkhalyuzin
 */
class ImplicitConversionIndex extends ScStringStubIndexExtension[ScMember] {
  override def getKey: StubIndexKey[String, ScMember] = ImplicitConversionIndex.indexKey
}

object ImplicitConversionIndex extends ImplicitIndex {
  //only implicit classes and implicit conversion defs are indexed
  //there is also a case when implicit conversion is provided by an implicit val with function type, but I think it is too exotic to support
  val indexKey: StubIndexKey[String, ScMember] = createIndexKey("sc.implicit.conversion")

  private val dummyStringKey: String = "implicit_conversion"

  def allElements(scope: GlobalSearchScope)
                 (implicit project: Project): Iterable[ScMember] = for {
    key <- allKeys(project)
    element <- elements(key, scope, classOf[ScMember])
  } yield element

  override def occurrences(sink: IndexSink, keys: Array[String]): Unit = {
    super.occurrences(sink, keys)
    //type of definition is missing or we couldn't extract class name from it
    if (keys.isEmpty) {
      occurrence(sink, dummyStringKey)
    }
  }
}