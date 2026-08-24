package org.jetbrains.sbt.project.runner

import org.jetbrains.plugins.scala.util.runners.{AbstractScalaBlockJUnit4ClassRunner, IdeaCustomParameterizedFilterable}
import org.jetbrains.sbt.project.{ImportMode, ImportingTestCase}
import org.junit.runner.Runner
import org.junit.runners.Suite
import org.junit.runners.model.{FrameworkMethod, InvalidTestClassError}

import scala.jdk.CollectionConverters.SeqHasAsJava

final class SbtProjectStructureImportingRunner(testClass: Class[?])
  extends Suite(testClass, SbtProjectStructureImportingRunner.createRunners(testClass).asJava)
    with IdeaCustomParameterizedFilterable

object SbtProjectStructureImportingRunner:
  private def createRunners(testClass: Class[?]): Seq[Runner] =
    val assignable = classOf[ImportingTestCase].isAssignableFrom(testClass)
    if !assignable then
      val notTestCase = new Exception(
        s"Test class ${testClass.getName} must extend ${classOf[ImportingTestCase].getSimpleName} to be runnable using the ${classOf[SbtProjectStructureImportingRunner].getSimpleName} runner"
      )
      throw InvalidTestClassError(testClass, java.util.List.of(notTestCase))

    ImportMode.values.map(ImportModeRunner(testClass, _)).toSeq
  end createRunners

  private final class ImportModeRunner(
    testClass: Class[?],
    importMode: ImportMode
  ) extends AbstractScalaBlockJUnit4ClassRunner(testClass, classOf[SbtProjectStructureImportingRunner].getSimpleName):

    override protected def createTest(): ImportingTestCase =
      val instance = getTestClass.getOnlyConstructor.newInstance().asInstanceOf[ImportingTestCase]
      instance.importMode = importMode
      instance

    override protected def getName: String = s"[${importMode.displayName}]"

    override protected def testName(method: FrameworkMethod): String = method.getName + getName
