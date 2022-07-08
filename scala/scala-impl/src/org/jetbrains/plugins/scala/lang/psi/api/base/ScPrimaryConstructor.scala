package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.light.ScPrimaryConstructorWrapper
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData}

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
  @CachedInUserData(this, BlockModificationTracker(this))
  override def effectiveParameterClauses: Seq[ScParameterClause] = {
    def emptyParameterList: ScParameterClause =
      ScalaPsiElementFactory.createEmptyClassParamClauseWithContext(parameterList)

    val clausesWithInitialEmpty = parameterList.clauses match {
      case Seq()                                   => Seq(emptyParameterList)
      case Seq(clause) if clause.isImplicitOrUsing => Seq(emptyParameterList, clause)
      case clauses                                 => clauses
    }

    clausesWithInitialEmpty ++
      ScalaPsiUtil.syntheticParamClause(containingClass, parameterList, isClassParameter = true)()
  }

  def effectiveFirstParameterSection: Seq[ScClassParameter] = effectiveParameterClauses.head.unsafeClassParameters

  @Cached(BlockModificationTracker(this), this)
  def getFunctionWrappers: Seq[ScPrimaryConstructorWrapper] = {
    val builder = ArraySeq.newBuilder[ScPrimaryConstructorWrapper]

    for {
      first <- parameterList.clauses.headOption
      if first.hasRepeatedParam
      if hasAnnotation("scala.annotation.varargs")
    } builder += new ScPrimaryConstructorWrapper(this, isJavaVarargs = true)

    builder += new ScPrimaryConstructorWrapper(this)
    builder.result()
  }
}

object ScPrimaryConstructor {
  object ofClass {
    def unapply(pc: ScPrimaryConstructor): Option[ScClass] = {
      pc.containingClass match {
        case c: ScClass => Some(c)
        case _ => None
      }
    }
  }
}