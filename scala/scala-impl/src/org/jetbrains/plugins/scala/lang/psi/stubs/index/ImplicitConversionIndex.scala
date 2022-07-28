package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexKeyExt

final class ImplicitConversionIndex extends ScStringStubIndexExtension[ScMember] {

  //noinspection TypeAnnotation
  override def getKey = ImplicitConversionIndex.indexKey
}

object ImplicitConversionIndex extends ImplicitIndex[ScMember] {

  //noinspection TypeAnnotation
  override protected val indexKey = ScalaIndexKeys.IMPLICIT_CONVERSION_KEY

  def conversionCandidatesForFqn(classFqn: String, scope: GlobalSearchScope)
                                (implicit project: Project): Iterable[ScFunction] =
    forClassFqn(classFqn, scope)
      .flatMap(findImplicitFunction)

  def allConversions(scope: GlobalSearchScope)
                    (implicit project: Project): Iterable[ScFunction] =
    indexKey.allKeys
      .flatMap(indexKey.elements(_, scope))
      .flatMap(findImplicitFunction)

  private def findImplicitFunction(member: ScMember): Option[ScFunction] = member match {
    case f: ScFunction => Option(f)
    case c: ScClass => c.getSyntheticImplicitMethod
    case _ => None
  }
}
