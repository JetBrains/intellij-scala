package org.jetbrains.sbt
package resolvers

import java.io.{IOException, File}
import java.net.URI


sealed trait ResolverException

final case class InvalidRepository(repo: String)
  extends IOException(SbtBundle("sbt.resolverIndexer.invalidRepository", repo))
  with ResolverException

final case class RepositoryIndexingException(repo: String, cause: Throwable)
  extends IOException(SbtBundle("sbt.resolverIndexer.remoteRepositoryHasNotBeenIndexed", repo, cause.toString))
  with ResolverException

final case class CantCreateIndexDirectory(dir: File)
  extends IOException(SbtBundle("sbt.resolverIndexer.cantCreateIndexDir", dir))
  with ResolverException

final case class IndexVersionMismatch(indexProperties: File)
  extends RuntimeException(SbtBundle("sbt.resolverIndexer.indexVersionMismatch", indexProperties))
  with ResolverException

final case class CorruptedIndexException(indexFile: File)
  extends IOException(SbtBundle("sbt.resolverIndexer.indexFileIsCorrupted", indexFile.getAbsolutePath))
  with ResolverException