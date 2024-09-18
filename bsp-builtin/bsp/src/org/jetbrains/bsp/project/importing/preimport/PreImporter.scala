package org.jetbrains.bsp.project.importing.preimport

/** A PreImporter is run before every import of a project */
abstract class PreImporter {
  def cancel(): Unit
}
