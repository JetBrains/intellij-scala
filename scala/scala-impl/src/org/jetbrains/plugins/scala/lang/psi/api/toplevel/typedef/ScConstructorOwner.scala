package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api._
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner, ScParameterOwnerBase}

/**
  * @author adkozlov
  */
trait ScConstructorOwnerBase extends ScTypeDefinitionBase with ScParameterOwnerBase { this: ScConstructorOwner =>

  def constructor: Option[ScPrimaryConstructor] =
    findChild[ScPrimaryConstructor]

  override def parameters: Seq[ScClassParameter] = constructor.toSeq.flatMap {
    _.effectiveParameterClauses
  } flatMap {
    _.unsafeClassParameters
  }

  def secondaryConstructors: Seq[ScFunction] =  functions.filter {
    _.isConstructor
  }

  def constructors: Seq[ScMethodLike] =
    secondaryConstructors ++ constructor

  override def clauses: Option[ScParameters] = constructor map {
    _.parameterList
  }

  override def members: Seq[ScMember] = super.members ++ constructor
}