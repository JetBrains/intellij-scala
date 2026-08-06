package org.jetbrains.plugins.scala.compiler.diagnostics

import org.jetbrains.jps.incremental.scala.Client.PosInfo

case class Action(title: String, edit: WorkspaceEdit)

case class WorkspaceEdit(changes: List[TextEdit])

case class TextEdit(start: PosInfo, end: PosInfo, newText: String)
