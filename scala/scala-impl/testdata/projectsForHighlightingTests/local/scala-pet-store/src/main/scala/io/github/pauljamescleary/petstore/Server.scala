package io.github.pauljamescleary.petstore

import cats.effect._
import cats.implicits._
import fs2.Stream
import io.github.pauljamescleary.petstore.config.{DatabaseConfig, PetStoreConfig}
import io.github.pauljamescleary.petstore.endpoint.{OrderEndpoints, PetEndpoints}
import io.github.pauljamescleary.petstore.repository.{
  DoobieOrderRepositoryInterpreter,
  DoobiePetRepositoryInterpreter
}
import io.github.pauljamescleary.petstore.service.{OrderService, PetService}
import io.github.pauljamescleary.petstore.validation.PetValidationInterpreter
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.util.StreamApp
import org.http4s.util.ExitCode

object Server extends StreamApp[IO] {

  override def stream(args: List[String], shutdown: IO[Unit]): Stream[IO, ExitCode] =
    createStream[IO](args, shutdown)

  def createStream[F[_]](args: List[String], shutdown: F[Unit])(
      implicit E: Effect[F]): Stream[F, ExitCode] =
    for {
      conf          <- Stream.eval(PetStoreConfig.load[F])
      xa            <- Stream.eval(DatabaseConfig.dbTransactor(conf.db))
      _             <- Stream.eval(DatabaseConfig.initializeDb(conf.db, xa))
      petRepo       =  DoobiePetRepositoryInterpreter[F](xa)
      orderRepo     =  DoobieOrderRepositoryInterpreter[F](xa)
      petValidation =  PetValidationInterpreter[F](petRepo)
      petService    =  PetService[F](petRepo, petValidation)
      orderService  =  OrderService[F](orderRepo)
      exitCode      <- BlazeBuilder[F]
        .bindHttp(8080, "localhost")
        .mountService(PetEndpoints.endpoints[F](petService), "/")
        .mountService(OrderEndpoints.endpoints[F](orderService), "/")
        .serve
    } yield exitCode
}
