package org.jetbrains.plugins.scala.compilation

import org.jetbrains.plugins.scala.{ScalaVersion, Scala_3_0}
import org.jetbrains.plugins.scala.compilation.CompilationTestBase.MethodInvocation
import org.jetbrains.plugins.scala.project.IncrementalityType

abstract class DottyCompilationTestBase(override val incrementalityType: IncrementalityType)
  extends CompilationTestBase {

  // TODO do we need this?
  override protected def supportedIn(version: ScalaVersion): Boolean = version.major == Scala_3_0.major

  override def githubUsername: String = "lampepfl"

  override def githubRepoName: String = "dotty-example-project"

  override def revision: String = "a6ffffe580a7a554bba8f37487a3c458fd8ae0cc"

  override protected def methodInvocation: Option[MethodInvocation] = Some(MethodInvocation(
    className = "Main",
    methodName = "main",
    args = Array(Array.empty[String])
  ))
}

class DottyIdeaCompilationTest extends DottyCompilationTestBase(IncrementalityType.IDEA)

class DottySbtCompilationTest extends DottyCompilationTestBase(IncrementalityType.SBT) {
  override protected def allowCompilationWarnings: Boolean = true // TODO use false instead
}
