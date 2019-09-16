package org.jetbrains.plugins.scala.lang.psi.uast.utils

import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

import scala.reflect.ClassTag

/**
  * Common resolve methods which help to follow DRY principle.
  */
object ResolveCommon {
  @Nullable
  def resolveNullable[T >: Null: ClassTag](ref: ScReference): T =
    resolveNullable(Some(ref))

  @Nullable
  def resolveNullable[T >: Null: ClassTag](refOpt: Option[ScReference]): T =
    resolve[T](refOpt).orNull

  def resolve[T >: Null: ClassTag](ref: ScReference): Option[T] =
    resolve(Some(ref))

  def resolve[T >: Null: ClassTag](refOpt: Option[ScReference]): Option[T] =
    refOpt.map(_.resolve()).collect { case psiElement: T => psiElement }
}
