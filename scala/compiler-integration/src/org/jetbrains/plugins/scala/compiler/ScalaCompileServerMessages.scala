package org.jetbrains.plugins.scala.compiler

import com.intellij.DynamicBundle
import org.jetbrains.jps.incremental.scala.ScalaCompileServerMessagesShared

//duplicated in org.jetbrains.jps.incremental.scala.ScalaCompileServerJpsBundle (but with JpsDynamicBundle)
object ScalaCompileServerMessages
  extends ScalaCompileServerMessagesShared(new DynamicBundle(classOf[ScalaCompileServerMessages.type], ScalaCompileServerMessagesShared.BUNDLE))
