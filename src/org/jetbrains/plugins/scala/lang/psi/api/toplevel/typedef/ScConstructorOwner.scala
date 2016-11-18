package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes.PRIMARY_CONSTRUCTOR
import org.jetbrains.plugins.scala.lang.psi.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScParameterOwner}

/**
  * @author adkozlov
  */
trait ScConstructorOwner extends ScParameterOwner with ScTemplateDefinition {
  def constructor: Option[ScPrimaryConstructor] =
    this match {
      case element: ScalaStubBasedElementImpl[_] if element.getStub != null =>
        element.getStub
          .getChildrenByType(PRIMARY_CONSTRUCTOR, JavaArrayFactoryUtil.ScPrimaryConstructorFactory)
          .headOption
      case _ => None
    }

  def parameters: Seq[ScClassParameter] =
    constructor.toSeq.flatMap {
      _.effectiveParameterClauses
    }.flatMap {
      _.unsafeClassParameters
    }

  def secondaryConstructors: Seq[ScFunction] =
    functions.filter {
      _.isConstructor
    }

  def constructors: Seq[PsiMethod] =
    secondaryConstructors ++ constructor

  def clauses: Option[ScParameters] =
    constructor.map {
      _.parameterList
    }

  override def members: Seq[ScMember] =
    super.members ++ constructor
}
