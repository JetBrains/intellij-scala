package org.jetbrains.plugins.scala.compiler.tracing.core.events

/**
 *  This event can be used to close any other event open in the context, keyed by `pKey`
 */
case class EndEvent(pKey: Any, reason: String) extends
  BaseEvent("End Event", Some(pKey), None, true, true, Map("reason" -> reason))
