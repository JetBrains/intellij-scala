package org.jetbrains.plugins.cbt

import java.io.File
import sys.process._

object CBT {
  def runAction(action: Seq[String], root: File): String =
    Process(Seq("cbt") ++ action, root) !!
}
