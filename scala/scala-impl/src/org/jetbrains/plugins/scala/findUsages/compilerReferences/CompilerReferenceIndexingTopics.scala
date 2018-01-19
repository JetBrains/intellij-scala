package org.jetbrains.plugins.scala.findUsages.compilerReferences

import com.intellij.util.messages.Topic

object CompilerReferenceIndexingTopics {
  val indexingStatus: Topic[CompilerReferenceIndexingStatusListener] =
    Topic.create[CompilerReferenceIndexingStatusListener]("compiler reference index build status", classOf[CompilerReferenceIndexingStatusListener])
}
