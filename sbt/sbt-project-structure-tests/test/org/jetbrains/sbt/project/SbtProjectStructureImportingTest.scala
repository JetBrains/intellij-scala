package org.jetbrains.sbt.project

/**
 * Runs the standard sbt project-structure importing suite with default import settings.
 *
 * The default setup uses regular structure extraction instead of sbt shell import and keeps
 * production and test sources in separate modules.
 */
final class SbtProjectStructureImportingTest extends SbtProjectStructureImportingSuiteBase
