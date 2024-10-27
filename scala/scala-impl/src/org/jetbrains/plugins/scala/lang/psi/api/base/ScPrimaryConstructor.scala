package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cached, cachedInUserData}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.light.ScPrimaryConstructorWrapper

import scala.collection.immutable.ArraySeq

trait ScPrimaryConstructor extends ScMember with ScMethodLike {

  /**
   *  @return has access modifier
   */
  def hasModifier: Boolean

  def getClassNameText: String

  override def parameterList: ScParameters

  override def clauses: Option[ScParameters] = Option(parameterList)

  override def parameters: Seq[ScClassParameter] = parameterList.clauses.flatMap(_.unsafeClassParameters)

  override def containingClass: ScTypeDefinition = getParent.asInstanceOf[ScTypeDefinition]

  /**
   * return only parameters, which are additionally members.
   */
  def valueParameters: Seq[ScClassParameter] = parameters.filter((p: ScClassParameter) => p.isVal || p.isVar)

  /**
   * All classes must have one non-implicit parameter list. If this is not declared in in the code,
   * it is assumed by the compiler.
   *
   * In addition, view and context bounds generate an additional implicit parameter section.
   */
  override def effectiveParameterClauses: Seq[ScParameterClause] =
    cachedInUserData("effectiveParameterClauses", this, BlockModificationTracker(this)) {
      def emptyParameterList: ScParameterClause =
        ScalaPsiElementFactory.createEmptyClassParamClauseWithContext(parameterList)

      val clausesWithInitialEmpty = parameterList.clauses match {
        case Seq()                                   => Seq(emptyParameterList)
        case Seq(clause) if clause.isImplicitOrUsing => Seq(emptyParameterList, clause)
        case clauses                                 => clauses
      }

      ScParameterOwner.insertSyntheticParameterClause(
        parameterList,
        clausesWithInitialEmpty,
        containingClass.typeParameters,
        isClassParameter = true
      )
    }

  def effectiveFirstParameterSection: Seq[ScClassParameter] = effectiveParameterClauses.head.unsafeClassParameters

  def getFunctionWrappers: Seq[ScPrimaryConstructorWrapper] = _getFunctionWrappers()

  private val _getFunctionWrappers = cached("getFunctionWrappers", BlockModificationTracker(this), () => {
    val builder = ArraySeq.newBuilder[ScPrimaryConstructorWrapper]

    for {
      first <- parameterList.clauses.headOption
      if first.hasRepeatedParam
      if hasAnnotation("scala.annotation.varargs")
    } builder += new ScPrimaryConstructorWrapper(this, isJavaVarargs = true)

    builder += new ScPrimaryConstructorWrapper(this)
    builder.result()
  })
}

object ScPrimaryConstructor {
  object ofClass {
    def unapply(pc: ScPrimaryConstructor): Option[ScClass] = {
      pc.containingClass match {
        case c: ScClass => Some(c)
        case _          => None
      }
    }
  }
}