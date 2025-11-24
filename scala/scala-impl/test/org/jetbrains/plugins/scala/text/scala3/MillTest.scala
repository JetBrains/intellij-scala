package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase
import org.jetbrains.plugins.scala.text.TextToTextTestBase.Content
import org.jetbrains.plugins.scala.text.scala3.MillTest.ScaladocAnnotation

class MillTest extends TextToTextTestBase(
  Seq(
    "com.lihaoyi" %% "mill-main" % "0.13.0-M1-43-b217bc",
  ),
  Seq("mill"), Set.empty, 134,
  Set.empty,
  withSources = true,
  Set(
    "mill.api.Result", // with Product with Serializable
    "mill.define.Command", // extends Task[T] vs Task
    "mill.define.Cross", // extends BaseClass, Module
    "mill.define.Discover", // Predef.String
    "mill.define.DynamicModule", // extends BaseClass, Module
    "mill.define.EnclosingClass", // Predef.Class
    "mill.define.EvaluatorProxy", // Predef.Map
    "mill.define.InputImpl", // immutable.Seq[_root_.mill.define.Task[?]] vs Nil.type
    "mill.define.Target", // extends Task, NamedTask
    "mill.define.TargetImpl", // extends Task, Target
    "mill.define.TaskModule", // extends BaseClass, Module
    "mill.define.Worker", // extends Task[T] vs Task
    "mill.define.internal.Cacher", // x$1.reflect.Symbol vs Any
    "mill.define.internal.CacherMacros", // Cannot resolve TypeRepr
    "mill.define.internal.CrossMacros", // Cannot resolve TypeRepr
    "mill.define.internal.ShimService", // Quotes
    "mill.main.SelectiveExecutionModule", // extends BaseClass, Module
    "mill.main.VcsVersion", // extends BaseClass, Module
    "mill.main.VisualizeModule", // private type, scala.Seq
  ),
  transformed = {
    case (Content.DecompiledVsSourceOutline, s) => ScaladocAnnotation.replaceAllIn(s, "")
    case (_, s) => s
  }
)

private object MillTest {
  private val ScaladocAnnotation = " *@_root_.mill.moduledefs.Scaladoc\\(.*?\\)\n".r
}