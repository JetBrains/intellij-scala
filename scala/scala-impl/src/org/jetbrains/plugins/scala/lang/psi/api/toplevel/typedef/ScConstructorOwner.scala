package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner}

/**
  * @author adkozlov
  */
trait ScConstructorOwner extends ScParameterOwner with ScTemplateDefinition {
  def constructor: Option[ScPrimaryConstructor] =
    findChild(classOf[ScPrimaryConstructor])

  def parameters: Seq[ScClassParameter] = constructor.toSeq flatMap {
    _.effectiveParameterClauses
  } flatMap {
    _.unsafeClassParameters
  }

  def secondaryConstructors: Seq[ScFunction] = functions filter {
    _.isConstructor
  }

  def constructors: Seq[ScMethodLike] =
    secondaryConstructors ++ constructor

  def clauses: Option[ScParameters] = constructor map {
    _.parameterList
  }

  override def members: Seq[ScMember] = super.members ++ constructor
}
