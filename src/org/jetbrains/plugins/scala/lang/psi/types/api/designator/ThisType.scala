package org.jetbrains.plugins.scala.lang.psi.types.api.designator

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor

import scala.collection.immutable.HashSet

/**
  * @author adkozlov
  */

/**
  * This type means type, which depends on place, where you want to get expression type.
  * For example
  *
  * class A       {
  * def foo: this.type = this
  * }
  *
  * class B extneds A       {
  * val z = foo // <- type in this place is B.this.type, not A.this.type
  * }
  *
  * So when expression is typed, we should replace all such types be return value.
  */
trait ThisType extends DesignatorOwner {
  override val element: ScTemplateDefinition
  override val isSingleton = true

  override private[types] def designatorSingletonType = None

  override private[types] def classType(project: Project, visitedAlias: HashSet[ScTypeAlias]) =
    Some(element, new ScSubstitutor(this))
}
