package org.jetbrains.plugins.scala.testingSupport.test.testdata

import java.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiClass, PsiPackage}
import org.jdom.Element
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt}
import org.jetbrains.plugins.scala.testingSupport.test.ui.TestRunConfigurationForm
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, SearchForTest, TestKind}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import scala.beans.BeanProperty
import scala.jdk.CollectionConverters._
import scala.collection.mutable.ArrayBuffer

class AllInPackageTestData(config: AbstractTestRunConfiguration) extends TestConfigurationData(config) {

  override type SelfType = AllInPackageTestData

  @BeanProperty var testPackagePath: String = ""
  // cache to be able to run configuration when in dumb mode
  @BeanProperty var classBuf: java.util.List[String] = new util.ArrayList[String]()

  override def getKind: TestKind = TestKind.ALL_IN_PACKAGE

  override def checkSuiteAndTestName: CheckResult =
    for {
      _ <- myCheckModule
      pack = JavaPsiFacade.getInstance(getProject).findPackage(testPackagePath)
      _ <- check(pack != null, configurationException(ScalaBundle.message("test.config.package.does.not.exist")))
    } yield ()

  private def myCheckModule: CheckResult = searchTest match {
    case SearchForTest.IN_WHOLE_PROJECT                                             => Right(())
    case SearchForTest.IN_SINGLE_MODULE | SearchForTest.ACCROSS_MODULE_DEPENDENCIES => checkModule
  }

  protected[test] def getPackage(path: String): PsiPackage = {
    ScPackageImpl.findPackage(getProject, path)
  }

  override def getTestMap: Map[String, Set[String]] = {
    val classFqns = if (isDumb) {
      if (classBuf.isEmpty) throw executionException(ScalaBundle.message("test.config.can.nott.run.while.indexing.no.class.names.memorized.from.previous.iterations"))
      classBuf.asScala
    } else {
      findTestSuites(getScope)
    }
    classBuf = classFqns.asJava
    classFqns.map(_ -> Set[String]()).toMap
  }

  private def findTestSuites(scope: GlobalSearchScope): Seq[String] = {
    val pack = ScPackageImpl(getPackage(testPackagePath))

    if (pack == null) throw executionException(ScalaBundle.message("test.run.config.test.package.not.found", testPackagePath))

    def collectClasses(pack: ScPackage, acc: ArrayBuffer[PsiClass] = ArrayBuffer.empty): collection.Seq[PsiClass] = {
      acc ++= pack.getClasses(scope)
      for (p <- pack.getSubPackages(scope))
        collectClasses(ScPackageImpl(p), acc)
      acc
    }

    val classesAll = collectClasses(pack)
    val classesUnique = classesAll.iterator.distinct.toSeq
    val classes = classesUnique.filter(c => config.isValidSuite(c) && config.canBeDiscovered(c))
    if (classes.isEmpty)
      throw executionException(ScalaBundle.message("test.config.did.not.find.suite.classes.in.package", pack.getQualifiedName))
    classes.map(_.qualifiedName)
  }

  private def getScope: GlobalSearchScope =
    getModule match {
      case null   => projectScope(getProject)
      case module =>
        searchTest match {
          case SearchForTest.IN_SINGLE_MODULE            => modulesScope(module)
          case SearchForTest.ACCROSS_MODULE_DEPENDENCIES => modulesScope(module.withDependencyModules: _*)
          case SearchForTest.IN_WHOLE_PROJECT            => projectScope(getProject)
        }
    }

  private def projectScope(project: Project): GlobalSearchScope =
    modulesScope(project.modules: _*)

  private def modulesScope(modules: Module*): GlobalSearchScope = {
    val moduleScopes = modules.map(GlobalSearchScope.moduleScope)
    GlobalSearchScope.union(moduleScopes.asJavaCollection)
  }

  override def copyFieldsFromForm(form: TestRunConfigurationForm): Unit = {
    super.copyFieldsFromForm(form)
    testPackagePath = form.getTestPackagePath
  }

  override protected def copyFieldsFrom(data: AllInPackageTestData): Unit = {
    super.copyFieldsFrom(data)
    data.classBuf = new util.ArrayList(classBuf)
  }

  override def copy(config: AbstractTestRunConfiguration): AllInPackageTestData = {
    val data = AllInPackageTestData(config, this.testPackagePath)
    data.copyFieldsFrom(this)
    data
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    JdomExternalizerMigrationHelper(element) { helper =>
      helper.migrateString("package")(testPackagePath = _)
    }
  }
}

object AllInPackageTestData {
  def apply(config: AbstractTestRunConfiguration, pack: String): AllInPackageTestData = {
    val res = new AllInPackageTestData(config)
    res.setTestPackagePath(pack)
    res
  }
}