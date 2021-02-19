package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods.DefinitionRole
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiTypedDefinitionWrapper, StaticPsiTypedDefinitionWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.macroAnnotations.Cached

/**
 * Member definitions, classes, named patterns which have types
 */
trait ScTypedDefinitionBase extends ScNamedElementBase with Typeable { this: ScTypedDefinition =>

  /**
   * @return false for variable elements
   */
  def isStable = true

  def isVar: Boolean = false
  def isVal: Boolean = false

  // TODO Add ScMember.isAbstract, alss see isAbstarct in ScValue / ScVariable
  def isAbstractMember: Boolean = nameContext match {
    case _: ScFunctionDefinition | _: ScPatternDefinition | _: ScVariableDefinition => false
    case _: ScClassParameter => false
    case _ => true
  }

  @Cached(BlockModificationTracker(this), this)
  def getTypedDefinitionWrapper(isStatic: Boolean, isAbstract: Boolean, role: DefinitionRole,
                                cClass: Option[PsiClass] = None): PsiTypedDefinitionWrapper = {
    new PsiTypedDefinitionWrapper(this, isStatic, isAbstract, role, cClass)
  }

  @Cached(BlockModificationTracker(this), this)
  def getStaticTypedDefinitionWrapper(role: DefinitionRole, cClass: PsiClassWrapper): StaticPsiTypedDefinitionWrapper = {
    new StaticPsiTypedDefinitionWrapper(this, role, cClass)
  }
}