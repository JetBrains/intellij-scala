package org.jetbrains.plugins.cbt

import java.io.File

import scala.xml._
import sys.process._

object CBT {
  def runAction(action: Seq[String], root: File): String =
    Process(Seq("cbt") ++ action, root) !!

  def projectBuidInfo(root: File): Node =
    XML.loadString(runAction(Seq("buildInfoXml"), root))
}
