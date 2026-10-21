package org.jetbrains.plugins.scala.compiler.highlighting.core

import com.intellij.util.messages.Topic

/**
 * Told when what is shown about blocked targets changes: when a compilation result arrives, or when a blocker is
 * edited and so stops being shown. An edit to anything else changes nothing, so nothing listening comes and goes
 * while the user types.
 *
 * Every way of telling the user about a blocked target, the traffic light or anything else, listens
 * here and reads the details it shows from [[CompilationLogicService.blockersOf]].
 *
 * @note Listeners run on the thread that changed the state; they must not assume the UI thread.
 */
private[highlighting] trait BlockedTargetsListener:
  def blockedChanged(): Unit

private[highlighting] object BlockedTargetsListener:
  val topic: Topic[BlockedTargetsListener] = new Topic(
    "compiler highlighting blocked targets changed",
    classOf[BlockedTargetsListener]
  )
