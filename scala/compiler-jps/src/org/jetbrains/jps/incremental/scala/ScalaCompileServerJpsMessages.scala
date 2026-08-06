package org.jetbrains.jps.incremental.scala

import org.jetbrains.jps.api.JpsDynamicBundle

import scala.annotation.nowarn

//duplicated in org.jetbrains.plugins.scala.compiler.ScalaCompileServerBundle (but with DynamicBundle)
object ScalaCompileServerJpsMessages extends ScalaCompileServerMessagesShared(MyBundle)
@nowarn("cat=deprecation")
private object MyBundle extends JpsDynamicBundle(ScalaCompileServerMessagesShared.BUNDLE)
