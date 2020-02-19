package org.jetbrains.plugins.scala.testingSupport.test.testdata

import java.util

import com.intellij.execution.ExecutionException
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{JavaPsiFacade, PsiClass, PsiPackage}
import org.jdom.Element
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.{SearchForTest, TestKind}
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestConfigurationType
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, TestRunConfigurationForm}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class AllInPackageTestData(config: AbstractTestRunConfiguration) extends TestConfigurationData(config) {

  override type SelfType = AllInPackageTestData

  @BeanProperty var testPackagePath: String = ""
  @BeanProperty var classBuf: java.util.List[String] = new util.ArrayList[String]()

  override def getKind: TestKind = TestKind.ALL_IN_PACKAGE

  override def getScope(withDependencies: Boolean): GlobalSearchScope = {
    searchTest match {
      case SearchForTest.IN_WHOLE_PROJECT => unionScope(_ => true, withDependencies)
      case SearchForTest.IN_SINGLE_MODULE if getModule != null => mScope(getModule, withDependencies)
      case SearchForTest.ACCROSS_MODULE_DEPENDENCIES if getModule != null =>
        unionScope(ModuleManager.getInstance(getProject).isModuleDependent(getModule, _), withDependencies)
      case _ => unionScope(_ => true, withDependencies)
    }
  }

  override def checkSuiteAndTestName: CheckResult =
    for {
      _ <- myCheckModule
      pack = JavaPsiFacade.getInstance(getProject).findPackage(getTestPackagePath)
      _ <- check(pack != null, exception(ScalaBundle.message("test.config.package.does.not.exist")))
    } yield ()

  private def myCheckModule: CheckResult = searchTest match {
    case SearchForTest.IN_WHOLE_PROJECT                                             => Right(())
    case SearchForTest.IN_SINGLE_MODULE | SearchForTest.ACCROSS_MODULE_DEPENDENCIES => checkModule
  }

  protected[test] def getPackage(path: String): PsiPackage = {
    ScPackageImpl.findPackage(getProject, path)
  }

  override def getTestMap: Map[String, Set[String]] = {
    def aMap(seq: Seq[String]) = Map(seq.map(_ -> Set[String]()):_*)
    if (isDumb) {
      if (classBuf.isEmpty) throw executionException(ScalaBundle.message("test.config.can.nott.run.while.indexing.no.class.names.memorized.from.previous.iterations"))
      return aMap(classBuf.asScala)
    }
    var classes = ArrayBuffer[PsiClass]()
    val pack = ScPackageImpl(getPackage(getTestPackagePath))
    val scope = getScope(withDependencies = false)

    if (pack == null) throw config.classNotFoundError

    def getClasses(pack: ScPackage): Seq[PsiClass] = {
      val buffer = new ArrayBuffer[PsiClass]

      buffer ++= pack.getClasses(scope)
      for (p <- pack.getSubPackages) {
        buffer ++= getClasses(ScPackageImpl(p))
      }
      if (config.configurationFactory.getType.isInstanceOf[UTestConfigurationType])
        buffer.filter {
          _.isInstanceOf[ScObject]
        }
      else buffer
    }

    for (cl <- getClasses(pack)) {
      if (config.isValidSuite(cl))
        classes += cl
    }
    if (classes.isEmpty)
      throw executionException(ScalaBundle.message("test.config.did.not.find.suite.classes.in.package", pack.getQualifiedName))
    val classFqns = classes.map(_.qualifiedName)
    classBuf = classFqns.asJava
    aMap(classFqns)
  }

  override def apply(form: TestRunConfigurationForm): Unit = {
    super.apply(form)
    testPackagePath = form.getTestPackagePath
  }

  override protected def apply(data: AllInPackageTestData): Unit = {
    super.apply(data)
    data.classBuf = new util.ArrayList(classBuf)
  }

  override def copy(config: AbstractTestRunConfiguration): AllInPackageTestData = {
    val data = AllInPackageTestData(config, this.testPackagePath)
    data.apply(this)
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