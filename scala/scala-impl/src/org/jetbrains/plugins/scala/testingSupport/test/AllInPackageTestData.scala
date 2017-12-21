package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RuntimeConfigurationException
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.JDOMExternalizer
import com.intellij.psi.{JavaPsiFacade, PsiClass, PsiPackage}
import com.intellij.psi.search.GlobalSearchScope
import org.jdom.Element
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.testingSupport.test.TestRunConfigurationForm.{SearchForTest, TestKind}
import org.jetbrains.plugins.scala.testingSupport.test.utest.UTestConfigurationType
import org.jetbrains.plugins.scala.extensions.PsiClassExt

import scala.collection.mutable.ArrayBuffer

class AllInPackageTestData(override val config: AbstractTestRunConfiguration) extends TestConfigurationData(config) {
  override def getScope(withDependencies: Boolean): GlobalSearchScope = {
    searchTest match {
      case SearchForTest.IN_WHOLE_PROJECT => unionScope(_ => true, withDependencies)
      case SearchForTest.IN_SINGLE_MODULE if getModule != null => mScope(getModule, withDependencies)
      case SearchForTest.ACCROSS_MODULE_DEPENDENCIES if getModule != null =>
        unionScope(ModuleManager.getInstance(getProject).isModuleDependent(getModule, _), withDependencies)
      case _ => unionScope(_ => true, withDependencies)
    }
  }

  override def checkSuiteAndTestName(): Unit = {
    searchTest match {
      case SearchForTest.IN_WHOLE_PROJECT =>
      case SearchForTest.IN_SINGLE_MODULE | SearchForTest.ACCROSS_MODULE_DEPENDENCIES => checkModule()
    }
    val pack = JavaPsiFacade.getInstance(getProject).findPackage(getTestPackagePath)
    if (pack == null) {
      throw new RuntimeConfigurationException("Package doesn't exist")
    }
  }

  protected[test] def getPackage(path: String): PsiPackage = {
    ScPackageImpl.findPackage(getProject, path)
  }

  override def getTestMap(): Map[String, Set[String]] = {
    def aMap(seq: Seq[String]) = Map(seq.map(_ -> Set[String]()):_*)
    if (isDumb) {
      if (classBuf.isEmpty) throw new ExecutionException("Can't run while indexing: no class names memorized from previous iterations.")
      return aMap(classBuf)
    }
    var classes = ArrayBuffer[PsiClass]()
    val pack = ScPackageImpl(getPackage(getTestPackagePath))
    val scope = getScope(withDependencies = false)

    if (pack == null) config.classNotFoundError

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
      if (!config.isInvalidSuite(cl))
        classes += cl
    }
    if (classes.isEmpty)
      throw new ExecutionException(s"Did not find suite classes in package ${pack.getQualifiedName}")
    val classFqns = classes.map(_.qualifiedName)
    classBuf = classFqns
    aMap(classFqns)
  }

  private var classBuf: Seq[String] = Seq()

  override def readExternal(element: Element): Unit = {
    import scala.collection.JavaConverters._
    testPackagePath = JDOMExternalizer.readString(element, "package")
    classBuf = JDOMExternalizer.loadStringsList(element, "buffered", "bufClass").asScala
  }

  override def writeExternal(element: Element): Unit = {
    JDOMExternalizer.write(element, "package", getTestPackagePath)
    JDOMExternalizer.saveStringsList(element, "buffered", "bufClass", classBuf.sorted:_*)
  }

  override def getKind: TestKind = TestKind.ALL_IN_PACKAGE

  override def apply(form: TestRunConfigurationForm): Unit = {
    testPackagePath = form.getTestPackagePath
  }
}

object AllInPackageTestData {
  def apply(config: AbstractTestRunConfiguration, pack: String) = {
    val res = new AllInPackageTestData(config)
    res.setTestPackagePath(pack)
    res
  }
}