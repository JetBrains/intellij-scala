package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import org.jetbrains.plugins.scala.extensions.OptionExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScConstructorOwner, ScEnum, ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ScEnumCase extends ScConstructorOwner {
  def enumParent: ScEnum

  /**
   * Returns type parameters from an explicit type parameter clause only,
   * as opposed to [[typeParameters]], which will also return
   * type params implicitly inherited from parent enum class.
   */
  def physicalTypeParameters: Seq[ScTypeParam]

  override def isCase: Boolean = true

  def getSyntheticCounterpart: ScNamedElement
}

object ScEnumCase {
  object SingletonCase {
    def unapply(cse: ScEnumCase): Option[(String, Seq[ScType])] =
      Option.when(cse.constructor.isEmpty)((cse.name, cse.superTypes))
  }

  object Original {
    def unapply(e: ScNamedElement): Option[ScEnumCase] =
      OriginalEnum.unapply(e).flatMap {
        enum =>
          val name = e.name
          enum.cases.find(_.name == name)
      }
  }

  object OriginalEnum {
    def unapply(e: ScNamedElement): Option[ScEnum] =
      ScalaPsiUtil.nameContext(e) match {
        case member: ScMember =>
          member.syntheticNavigationElement match {
            case obj: ScObject => obj.baseCompanion.filterByType[ScEnum]
            case _             => None
          }
        case _ => None
      }
  }

  def isDesugaredEnumCase(cls: ScNamedElement): Boolean =
    OriginalEnum.unapply(cls).isDefined
}
