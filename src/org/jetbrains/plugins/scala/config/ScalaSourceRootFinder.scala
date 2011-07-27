package org.jetbrains.plugins.scala.config

import com.intellij.ide.util.newProjectWizard.SourceRootFinder
import java.io.File
import java.util.List
import com.intellij.openapi.util.Pair
import com.intellij.ide.util.JavaUtil
import org.jetbrains.plugins.scala.ScalaFileType

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaSourceRootFinder extends SourceRootFinder {
  def findRoots(dir: File): List[Pair[File, String]] = {
    ScalaDirUtil.suggestRoots(dir, ScalaFileType.SCALA_FILE_TYPE)
  }

  def getDescription: String = null

  def getName: String = "Scala"
}