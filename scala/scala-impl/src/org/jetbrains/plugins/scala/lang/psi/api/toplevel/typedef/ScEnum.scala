package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunctionDefinition}
import org.jetbrains.plugins.scala.util.ScalaUsageNamesUtil

trait ScEnum extends ScConstructorOwner with ScDerivesClauseOwner {
  def cases: Seq[ScEnumCase]

  def syntheticClass: Option[ScTypeDefinition]
}

object ScEnum {
  object Original {
    def unapply(cls: ScClass): Option[ScEnum] =
      Option(cls.originalEnumElement)
  }

  object OriginalFromObject {
    def unapply(obj: ScObject): Option[ScEnum] = Option(obj.syntheticNavigationElement) match {
        case Some(cls: ScClass) => Original.unapply(cls)
        case _ => None
      }
  }

  object OriginalFromSyntheticMethod {
    def unapply(functionDefinition: ScFunctionDefinition): Option[ScEnum] =
      if (ScalaUsageNamesUtil.enumSyntheticMethodNames.contains(functionDefinition.name))
        functionDefinition.context match {
          case cls: ScClass if cls.syntheticNavigationElement != null =>
            cls.syntheticNavigationElement match {
              case enum: ScEnum => Some(enum)
              case _ => None
            }
          case _ => None
        }
      else None
  }

  def isDesugaredEnumClass(cls: ScTypeDefinition): Boolean =
    cls.originalEnumElement ne null
}
