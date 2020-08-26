package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner}

/**
  * @author adkozlov
  */
trait ScConstructorOwner extends ScTypeDefinition
  with ScParameterOwner {

  def constructor: Option[ScPrimaryConstructor] =
    findChild(classOf[ScPrimaryConstructor])

  override def parameters: Seq[ScClassParameter] = constructor.toSeq.flatMap {
    _.effectiveParameterClauses
  } flatMap {
    _.unsafeClassParameters
  }

  def secondaryConstructors: collection.Seq[ScFunction] =  functions.filter {
    _.isConstructor
  }

  def constructors: collection.Seq[ScMethodLike] =
    secondaryConstructors ++ constructor

  override def clauses: Option[ScParameters] = constructor map {
    _.parameterList
  }

  override def members: collection.Seq[ScMember] = super.members ++ constructor
}
