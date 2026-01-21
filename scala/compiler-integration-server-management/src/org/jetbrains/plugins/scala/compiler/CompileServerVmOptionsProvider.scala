package org.jetbrains.plugins.scala.compiler

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.ExtensionPointDeclaration

@deprecated(
  message = """
Unused extension point. Any registered implementations will be ignored.
If you still need to implement this extension point, please contact the Scala Plugin team on the Discord Server or
open a YouTrack issue.
The interface is kept in place to keep binary compatibility for a while. The code will be removed in 2026.2
""",
  since = "2026.1"
)
@Deprecated(since = "2026.1", forRemoval = true)
@ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
abstract class CompileServerVmOptionsProvider {
  def vmOptionsFor(project: Project): Seq[String]
}

@deprecated(
  message = """
Unused extension point. Any registered implementations will be ignored.
If you still need to implement this extension point, please contact the Scala Plugin team on the Discord Server or
open a YouTrack issue.
The interface is kept in place to keep binary compatibility for a while. The code will be removed in 2026.2
""",
  since = "2026.1"
)
@Deprecated(since = "2026.1", forRemoval = true)
@ApiStatus.ScheduledForRemoval(inVersion = "2026.2")
object CompileServerVmOptionsProvider
  extends ExtensionPointDeclaration[CompileServerVmOptionsProvider]("org.intellij.scala.compileServerVmOptionsProvider")
