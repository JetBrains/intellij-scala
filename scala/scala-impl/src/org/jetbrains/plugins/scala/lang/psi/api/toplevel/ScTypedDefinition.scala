package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cached}
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods.DefinitionRole
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiTypedDefinitionWrapper, StaticPsiTypedDefinitionWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

/**
 * Member definitions, classes, named patterns which have types
 */
trait ScTypedDefinition extends ScNamedElement with Typeable {

  /**
   * @return true - if the definition has a stable type<br>
   *         false - otherwise
   *
   *       This particular method is about "stable type"
   */
  def isStable = true

  def isVar: Boolean = false
  def isVal: Boolean = false

  // TODO Add ScMember.isAbstract, also see isAbstarct in ScValue / ScVariable
  def isAbstractMember: Boolean = nameContext match {
    case _: ScFunctionDefinition | _: ScPatternDefinition | _: ScVariableDefinition => false
    case _: ScClassParameter => false
    case _ => true
  }

  def getTypedDefinitionWrapper(isStatic: Boolean, isAbstract: Boolean, role: DefinitionRole, cClass: Option[PsiClass] = None): PsiTypedDefinitionWrapper = _getTypedDefinitionWrapper(isStatic, isAbstract, role, cClass)

  private val _getTypedDefinitionWrapper = cached("getTypedDefinitionWrapper", BlockModificationTracker(this), (isStatic: Boolean, isAbstract: Boolean, role: DefinitionRole, cClass: Option[PsiClass]) => {
    new PsiTypedDefinitionWrapper(this, isStatic, isAbstract, role, cClass)
  })

  def getStaticTypedDefinitionWrapper(role: DefinitionRole, cClass: PsiClassWrapper): StaticPsiTypedDefinitionWrapper = _getStaticTypedDefinitionWrapper(role, cClass)

  private val _getStaticTypedDefinitionWrapper = cached("getStaticTypedDefinitionWrapper", BlockModificationTracker(this), (role: DefinitionRole, cClass: PsiClassWrapper) => {
    new StaticPsiTypedDefinitionWrapper(this, role, cClass)
  })
}
