package io.github.pauljamescleary.petstore.config

import cats.effect.Effect
import pureconfig.error.ConfigReaderException

case class PetStoreConfig(db: DatabaseConfig)

object PetStoreConfig {

  import pureconfig._

  /**
    * Loads the pet store config using PureConfig.  If configuration is invalid we will
    * return an error.  This should halt the application from starting up.
    */
  def load[F[_]](implicit E: Effect[F]): F[PetStoreConfig] =
    loadConfig[PetStoreConfig]("petstore") match {
      case Right(ok) => E.pure(ok)
      case Left(e) => E.raiseError(new ConfigReaderException[PetStoreConfig](e))
    }
}
