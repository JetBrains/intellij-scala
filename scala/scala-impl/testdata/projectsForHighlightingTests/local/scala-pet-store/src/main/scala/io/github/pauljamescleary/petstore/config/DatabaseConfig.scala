package io.github.pauljamescleary.petstore.config

import cats.effect.{Async, Sync}
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway

case class DatabaseConfig(url: String, driver: String, user: String, password: String)

object DatabaseConfig {

  def dbTransactor[F[_]: Async](dbConfig: DatabaseConfig): F[HikariTransactor[F]] =
    HikariTransactor[F](dbConfig.driver, dbConfig.url, dbConfig.user, dbConfig.password)

  /**
    * Runs the flyway migrations against the target database
    *
    * This only gets applied if the database is H2, our local in-memory database.  Otherwise
    * we skip this step
    */
  def initializeDb[F[_]](dbConfig: DatabaseConfig, xa: HikariTransactor[F])(
      implicit S: Sync[F]): F[Unit] =
    if (dbConfig.url.contains(":h2:")) {
      xa.configure { ds =>
        val fw = new Flyway()
        fw.setDataSource(ds)
        fw.migrate()
        ()
      }
    } else {
      S.pure(())
    }
}
