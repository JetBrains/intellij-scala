package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.types.api.StdTypes
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Left, Right, TypeResult}

import scala.language.implicitConversions

trait ScType extends project.ProjectContextOwner {

  // TODO Defined in ScType to minimize the amount of imports. The hypothesis is that we can probably remove most of these methods.

  def isLeft: Boolean = this.is[Failure]

  def isRight: Boolean = !this.is[Failure]

  def getRight: this.type = this match {
    case f: Failure => throw new NoSuchElementException(f.cause)
    case _ => this
  }

  def getOrElse[B >: this.type](default: => B): B = this match {
    case _: Failure => default
    case _ => this
  }

  def toOption: Option[this.type] = this match {
    case _: Failure => None
    case _ => Some(this)
  }

  def toEither: scala.Either[Failure, this.type] = this match {
    case f: Failure => scala.Left(f)
    case _ => scala.Right(this)
  }

  def toSeq: Seq[this.type] = this match {
    case _: Failure => Seq.empty
    case _ => Seq(this)
  }

  def foreach(f: this.type => Unit): Unit = this match {
    case _: Failure =>
    case _ => f(this)
  }

  def map[B <: ScType](f: this.type => B): TypeResult = this match {
    case f: Failure => f
    case _ => f(this).ensuring(_.isRight)
  }

  def mapToOption[B](f: this.type => B): Option[B] = this match {
    case _: Failure => None
    case _ => Some(f(this))
  }

  def flatMap(f: this.type => TypeResult): TypeResult = this match {
    case f: Failure => f
    case _ => f(this)
  }

  def leftFlatMap(f: Failure => TypeResult): TypeResult = this match {
    case t: Failure => f(t)
    case _ => Failure("")
  }

  def exists(p: this.type => Boolean): Boolean = this match {
    case _: Failure => false
    case _ => p(this)
  }

  def fold[B](f1: Failure => B, f2: this.type => B): B = this match {
    case f: Failure => f1(f)
    case _ => f2(this)
  }

  def get: ScType = getOrApiType(null)

  def getOrAny: ScType = getOrApiType(_.Any)

  def getOrNothing: ScType = getOrApiType(_.Nothing)

  private def getOrApiType(apiType: StdTypes => ScType): ScType = this match {
    case Right(value) => value
    case Left(failure) if apiType != null => apiType(failure.context.stdTypes)
    case _ => throw new NoSuchElementException("Failure.get")
  }

  def typeSystem: api.TypeSystem = projectContext.typeSystem

  private var aliasTypeInner: Option[AliasType] = _

  final def aliasType: Option[AliasType] = {
    if (aliasTypeInner == null) {
      ProgressManager.checkCanceled()
      aliasTypeInner = calculateAliasType
    }
    aliasTypeInner
  }

  final def isAliasType: Boolean = aliasType.isDefined

  private var unpacked: ScType = _

  final def unpackedType: ScType = {
    if (unpacked == null) {
      ProgressManager.checkCanceled()
      unpacked = unpackedTypeInner
    }
    unpacked
  }

  protected def calculateAliasType: Option[AliasType] = None

  // TODO: we must not override toString which does such a complex stuff (resolve, tree traversal etc...)
  //  for such things we should always use explicit methods oText/mkString/presentableText/etc...
  override final def toString: String = extensions.ifReadAllowed {
    presentableText(TypePresentationContext.emptyContext)
  }(getClass.getSimpleName)

  def isValue: Boolean

  def isFinalType: Boolean = false

  def inferValueType: api.ValueType

  protected def unpackedTypeInner: ScType = ScExistentialType(this) match {
    case ScExistentialType(q, Seq())                                       => q
    case ScExistentialType(arg: ScExistentialArgument, Seq(w)) if w == arg => arg.upper
    case ex                                                                => ex
  }

  def equivInner(r: ScType, constraints: ConstraintSystem, falseUndef: Boolean): ConstraintsResult = {
    ConstraintsResult.Left
  }

  def visitType(visitor: ScalaTypeVisitor): Unit

  def typeDepth: Int = 1

  def presentableText(implicit context: TypePresentationContext): String =
    typeSystem.presentableText(this)

  def urlText(implicit context: TypePresentationContext): String =
    typeSystem.urlText(this)

  def canonicalText: String = typeSystem.canonicalText(this)
}

object ScType {
  implicit def recursiveExtensions(tp: ScType): recursiveUpdate.Extensions = new recursiveUpdate.Extensions(tp)
}

trait NamedType extends ScType {
  val name: String

  override def presentableText(implicit context: TypePresentationContext): String = name

  override def canonicalText: String = name
}
