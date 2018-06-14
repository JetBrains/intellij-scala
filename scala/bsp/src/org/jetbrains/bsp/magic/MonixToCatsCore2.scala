package org.jetbrains.bsp.magic

import monix.types.{Monad, MonadRec}

object monixToCats extends MonixToCatsCore

/**
  * Copied from monix-cats because the full integration is incompatible with cats 1.0
  */
private[magic] trait MonixToCatsCore  {
  /** Converts Monix's [[monix.types.Monad Monad]] instances into
    * the Cats `Monad`.
    *
    * You can import [[monixToCatsMonad]] in scope, or initiate/extend
    * the [[MonixToCatsMonad]] class.
    */
  implicit def monixToCatsMonad[F[_] : Monad]: _root_.cats.Monad[F] =
    new MonixToCatsMonad[F]()

  /** Converts Monix's [[monix.types.Monad Monad]] instances into
    * the Cats `Monad`.
    *
    * You can import [[monixToCatsMonad]] in scope, or initiate/extend
    * the [[MonixToCatsMonad]] class.
    */
  class MonixToCatsMonad[F[_]](implicit F: Monad[F]) extends _root_.cats.Monad[F] {
    override def map[A, B](fa: F[A])(f: (A) => B): F[B] =
      F.functor.map(fa)(f)
    override def pure[A](x: A): F[A] =
      F.applicative.pure(x)
    override def ap[A, B](ff: F[(A) => B])(fa: F[A]): F[B] =
      F.applicative.ap(ff)(fa)
    override def map2[A, B, Z](fa: F[A], fb: F[B])(f: (A, B) => Z): F[Z] =
      F.applicative.map2(fa,fb)(f)
    override def product[A, B](fa: F[A], fb: F[B]): F[(A, B)] =
      F.applicative.map2(fa,fb)((a,b) => (a,b))
    override def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B] =
      F.flatMap(fa)(f)
    override def flatten[A](ffa: F[F[A]]): F[A] =
      F.flatten(ffa)

    override def tailRecM[A, B](a: A)(f: (A) => F[Either[A, B]]): F[B] = {
      val instance = F.asInstanceOf[AnyRef]

      instance match {
        case ref: MonadRec[_] =>
          // Workaround for Cats Monad instances that might implement
          // a stack-safe `tailRecM`, since unfortunately the
          // `RecursiveTailRecM` marker and the `FlatMapRec` type
          // are now gone and all monads are expected to implement
          // a safe `tailRecM`, which is not really possible
          ref.asInstanceOf[MonadRec[F]].tailRecM(a)(f)
        case _ =>
          MonadRec.defaultTailRecM(a)(f)(F)
      }
    }
  }
}