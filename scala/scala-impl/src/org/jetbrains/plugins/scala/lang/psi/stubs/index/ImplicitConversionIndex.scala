package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IndexSink
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

/**
 * @author Alexander Podkhalyuzin
 */
final class ImplicitConversionIndex extends ScStringStubIndexExtension[ScMember] {

  //noinspection TypeAnnotation
  override def getKey = ImplicitConversionIndex.indexKey
}

object ImplicitConversionIndex extends ImplicitIndex {

  import ScalaIndexKeys._

  //noinspection TypeAnnotation
  override protected val indexKey = IMPLICIT_CONVERSION_KEY

  private val dummyStringKey: String = "implicit_conversion"

  def allConversions(scope: GlobalSearchScope)
                    (implicit project: Project): Iterable[ScFunction] = for {
    key      <- indexKey.allKeys
    member   <- indexKey.elements(key, scope)

    function <- member match {
      case f: ScFunction => f :: Nil
      case c: ScClass => c.getSyntheticImplicitMethod.toList
      case _ => Nil
    }
  } yield function

  override def occurrences(sink: IndexSink, names: Array[String]): Unit = {
    super.occurrences(sink, names)
    //type of definition is missing or we couldn't extract class name from it
    if (names.isEmpty) {
      occurrence(sink, dummyStringKey)
    }
  }
}