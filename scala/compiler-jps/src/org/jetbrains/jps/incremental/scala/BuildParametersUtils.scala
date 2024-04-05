package org.jetbrains.jps.incremental.scala

object BuildParametersUtils {

  def getProcessDependenciesRecursivelyProperty: Boolean = {
    // "sbt.process.dependencies.recursively" property is the negation of the SbtProjectSettings.insertProjectTransitiveDependencies value
    // (see ScalaBuildProcessParametersProvider.transitiveProjectDependenciesParams).
    // So the default value for "sbt.process.dependencies.recursively" is set to
    // the opposite of the default value of SbtProjectSettings.insertProjectTransitiveDependencies
    Option(System.getProperty("sbt.process.dependencies.recursively")).flatMap(_.toBooleanOption).getOrElse(false)
  }
}
