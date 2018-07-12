package org.jetbrains.plugins.scala.lang.psi.types.api.designator

import com.intellij.psi.{PsiClass, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ValueType
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author adkozlov
  */
trait DesignatorOwner extends ValueType {
  val element: PsiNamedElement

  override implicit def projectContext: ProjectContext = element

  val isSingleton: Boolean = element match {
    case typedDefinition: ScTypedDefinition => typedDefinition.isStable
    case _ => false
  }

  def isStable: Boolean = isSingleton || element.isInstanceOf[ScObject]

  override def isFinalType: Boolean = element match {
    case clazz: PsiClass if clazz.isEffectivelyFinal => true
    case _ => false
  }

  private[types] def designatorSingletonType = element match {
    case _: ScObject => None
    case parameter: ScParameter if parameter.isStable => parameter.getRealParameterType.toOption
    case definition: ScTypedDefinition if definition.isStable => definition.`type`().toOption
    case _ => None
  }
}

object DesignatorOwner {

  def unapply(`type`: ScType): Option[PsiNamedElement] = `type` match {
    case owner: DesignatorOwner => Some(owner.element)
    case _ => None
  }
}
