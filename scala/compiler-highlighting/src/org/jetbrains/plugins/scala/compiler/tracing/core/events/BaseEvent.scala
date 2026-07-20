package org.jetbrains.plugins.scala.compiler.tracing.core.events

/** Base implementation of a context-carrying trace event */
class BaseEvent(override val name: String = "",
                override val parentKey: Option[Any] = None,
                override val key: Option[Any] = None,
                override val closeParent: Boolean = true,
                override val closeOnEnd: Boolean = false,
                override val args: Map[String, String] = Map(),
                override val category:Option[String] = Option.empty) extends ContextTraceEvent