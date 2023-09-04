package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScFunctionDefinition}
import org.jetbrains.plugins.scala.util.ScalaUsageNamesUtil

trait ScEnum extends ScConstructorOwner with ScDerivesClauseOwner {
  def cases: Seq[ScEnumCase]
}

object ScEnum {
  object FromObject {
    def unapply(obj: ScObject): Option[ScEnum] = obj.syntheticNavigationElement match {
      case cls: ScEnum => Some(cls)
      case _ => None
    }
  }

  object FromSyntheticMethod {
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
}
