package org.jetbrains.plugins.scala.text.scala3

import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
import org.jetbrains.plugins.scala.text.TextToTextTestBase
import org.jetbrains.plugins.scala.text.TextToTextTestBase.Content

class MillTest extends TextToTextTestBase(
  dependencies = Seq(
    "com.lihaoyi" %% "mill-main" % "0.13.0-M1-43-b217bc",
  ),
  packages = Seq("mill"),
  minClassCount = 134,
  withSources = true,
  sourceExceptions = Set(
    "mill.api.Result", // with Product with Serializable
    "mill.define.Command", // extends Task[T] vs Task
    "mill.define.InputImpl", // immutable.Seq[_root_.mill.define.Task[?]] vs Nil.type
    "mill.define.TargetImpl", // extends Task[T] vs Task
    "mill.define.Worker", // extends Task[T] vs Task
    "mill.define.internal.Cacher", // x$1.reflect.Symbol vs Any
    "mill.define.internal.CacherMacros", // Cannot resolve TypeRepr
    "mill.define.internal.CrossMacros", // Cannot resolve TypeRepr
    "mill.define.internal.ShimService", // Quotes
    "mill.main.VisualizeModule", // private type
  ),
  transformed = {
    case (Content.DecompiledVsSourceOutline, s) =>
      s.replaceAll(" *@_root_.mill.moduledefs.Scaladoc\\(.*?\\)\n", "")
    case (_, s) => s
  }
)
