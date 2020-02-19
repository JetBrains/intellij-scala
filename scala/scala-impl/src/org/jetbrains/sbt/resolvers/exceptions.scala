package org.jetbrains.sbt
package resolvers

import java.io.{File, IOException}


sealed trait ResolverException

final case class InvalidRepository(repo: String)
  extends IOException(SbtBundle.message("sbt.resolverIndexer.invalidRepository", repo))
  with ResolverException

final case class RepositoryIndexingException(repo: String, cause: Throwable)
  extends IOException(SbtBundle.message("sbt.resolverIndexer.remoteRepositoryHasNotBeenIndexed", repo, cause.toString))
  with ResolverException

final case class CantCreateIndexDirectory(dir: File)
  extends IOException(SbtBundle.message("sbt.resolverIndexer.cantCreateIndexDir", dir))
  with ResolverException

final case class IndexVersionMismatch(indexProperties: File)
  extends RuntimeException(SbtBundle.message("sbt.resolverIndexer.indexVersionMismatch", indexProperties))
  with ResolverException

final case class CorruptedIndexException(indexFile: File)
  extends IOException(SbtBundle.message("sbt.resolverIndexer.indexFileIsCorrupted", indexFile.getAbsolutePath))
  with ResolverException