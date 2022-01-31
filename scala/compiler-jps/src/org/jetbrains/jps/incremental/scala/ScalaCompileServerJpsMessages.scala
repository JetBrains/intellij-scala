package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.api.JpsDynamicBundle

//duplicated in org.jetbrains.plugins.scala.compiler.ScalaCompileServerBundle (but with DynamicBundle)
object ScalaCompileServerJpsMessages extends ScalaCompileServerMessagesShared(MyBundle)
private object MyBundle extends JpsDynamicBundle(ScalaCompileServerMessagesShared.BUNDLE)
