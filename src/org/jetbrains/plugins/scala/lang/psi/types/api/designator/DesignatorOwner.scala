package org.jetbrains.plugins.scala.lang.psi.types.api.designator

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeInTypeSystem, TypeSystem, ValueType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScTypeExt}

import scala.collection.immutable.HashSet

/**
  * @author adkozlov
  */
trait DesignatorOwner extends ValueType with TypeInTypeSystem {
  val element: PsiNamedElement
  override implicit val typeSystem: TypeSystem = element.typeSystem

  val isSingleton = element match {
    case typedDefinition: ScTypedDefinition => typedDefinition.isStable
    case _ => false
  }

  def isStable: Boolean = isSingleton || element.isInstanceOf[ScObject]

  override def isFinalType: Boolean = element match {
    case clazz: PsiClass if clazz.isEffectivelyFinal => true
    case _ => false
  }

  private[types] def designatorSingletonType = element match {
    case scObject: ScObject => None
    case parameter: ScParameter if parameter.isStable => parameter.getRealParameterType().toOption
    case definition: ScTypedDefinition if definition.isStable => definition.getType().toOption
    case _ => None
  }

  private[types] def designated(implicit withoutAliases: Boolean): Option[(PsiNamedElement, ScSubstitutor)] = element match {
    case definition: ScTypeAliasDefinition if withoutAliases =>
      definition.aliasedType().toOption.flatMap {
        _.extractDesignated
      }
    case _ => Some(element, ScSubstitutor.empty)
  }

  private[types] def classType(project: Project,
                               visitedAlias: HashSet[ScTypeAlias]): Option[(PsiClass, ScSubstitutor)] = element match {
    case clazz: PsiClass => Some(clazz, ScSubstitutor.empty)
    case definition: ScTypeAliasDefinition if !visitedAlias.contains(definition) =>
      definition.aliasedType.toOption.flatMap {
        _.extractClassType(project, visitedAlias + definition)
      }
    case _ => None
  }
}
