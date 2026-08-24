package org.jetbrains.plugins.scala.util.runners

import org.junit.Test
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.{FrameworkMethod, InvalidTestClassError}

import java.lang.reflect.{Method, Modifier}

abstract class AbstractScalaBlockJUnit4ClassRunner(testClass: Class[?], runnerClassName: String) extends BlockJUnit4ClassRunner(testClass) {

  validateNoUnmigratedJUnit3TestDefinitions(testClass)

  protected def createTest(): AnyRef

  protected def getName: String

  protected def testName(method: FrameworkMethod): String

  private def validateNoUnmigratedJUnit3TestDefinitions(testClass: Class[?]): Unit = {
    val allPublicMethods = testClass.getMethods
    val unmigratedMethods = allPublicMethods.filter(isUnmigratedJUnit3TestDefinition)
    if (unmigratedMethods.nonEmpty) {
      val message = unmigratedMethods.map(m => s"     - ${m.getName}")
        .mkString(
          start = s"The test class ${testClass.getName} contains unmigrated JUnit 3 style test methods:\n",
          sep = "\n",
          end = s"\n     Please annotate these methods with @org.junit.Test to make them executable with $runnerClassName."
        )
      val exception = new Exception(message)
      throw new InvalidTestClassError(testClass, java.util.List.of(exception))
    }
  }

  private def isUnmigratedJUnit3TestDefinition(method: Method): Boolean = {
    val isPublic = Modifier.isPublic(method.getModifiers)
    val startsWithTest = method.getName.startsWith("test")
    val hasZeroParameters = method.getParameters.isEmpty
    val returnsVoid = method.getReturnType == java.lang.Void.TYPE
    val hasTestAnnotation = method.getAnnotation(classOf[Test]) != null
    isPublic && startsWithTest && hasZeroParameters && returnsVoid && !hasTestAnnotation
  }
}
