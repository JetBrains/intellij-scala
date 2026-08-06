package org.jetbrains.jps.incremental.scala.local

import org.jetbrains.jps.incremental.scala.{Client, DelegateClient}

import java.nio.file.Path

private final class CollectingSourcesClient(client: Client) extends DelegateClient(client) {

  var sources: Set[Path] = Set.empty

  override def generated(source: Path, module: Path, name: String): Unit = {
    super.generated(source, module, name)
    sources += source
  }
}
