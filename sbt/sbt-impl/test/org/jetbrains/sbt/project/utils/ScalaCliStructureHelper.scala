package org.jetbrains.sbt.project.utils

import scala.util.matching.Regex

/**
 * In the Scala CLI project only the root project module is created (at least at this point).
 * It contains a project directory name with a suffix <code>_hash</code>.
 * The hash is created based on options passed <code>setup-ide</code> command.
 * The hash can change when:
 * <ul>
 * <li>the options passed to <code>setup-ide</code> are changed</li>
 * <li>the options are changed on the Scala CLI side (e.g. they add or remove some)</li>
 * </ul>
 *
 * @param projectNameRegex a grouped regular expression in a form <code>($scalaCliProjectName)_[a-zA-Z0-9]+</code>.
 *                         The group with the scala CLI project name will be later reused to change the value in a module/library/dependency name from e.g.
 *                         <code>myProjectName_0987hjks3a</code> to <code>myProjectName</code>.
 * @see [[org.jetbrains.sbt.project.ProjectStructureMatcher#convertIfScalaCli]]
 */
case class ScalaCliStructureHelper(projectNameRegex: Regex)

object ScalaCliStructureHelper {
  def apply(scalaCliProjectName: String): ScalaCliStructureHelper = {
    val projectNameRegex = s"($scalaCliProjectName)_[a-zA-Z0-9]+".r
    ScalaCliStructureHelper(projectNameRegex)
  }
}