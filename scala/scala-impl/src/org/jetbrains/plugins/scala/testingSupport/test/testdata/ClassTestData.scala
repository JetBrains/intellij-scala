package org.jetbrains.plugins.scala.testingSupport.test.testdata

import com.intellij.psi.PsiClass
import org.apache.commons.lang3.StringUtils
import org.jdom.Element
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.testingSupport.test.ui.TestRunConfigurationForm
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestRunConfiguration, TestKind}
import org.jetbrains.plugins.scala.util.JdomExternalizerMigrationHelper

import scala.beans.BeanProperty

class ClassTestData(config: AbstractTestRunConfiguration) extends TestConfigurationData(config) {

  override type SelfType = ClassTestData

  @BeanProperty var testClassPath: String = ""

  override def getKind: TestKind = TestKind.CLAZZ

  protected[test] def getClassPathClazz: PsiClass = config.getClazz(getTestClassPath)

  override def checkSuiteAndTestName: CheckResult =
    for {
      _ <- checkModule
      _ <- check(StringUtils.isNotBlank(getTestClassPath), configurationException(ScalaBundle.message("test.config.test.class.is.not.specified")))
      testClass = getClassPathClazz
      _ <- check(testClass != null, configurationException(ScalaBundle.message("test.config.test.class.not.found.in.module", getTestClassPath, getModule.getName)))
      //TODO: config.isInvalidSuite calls config.getSuiteClass and we call config.getSuiteClass again on the next line
      //  we should refactor how isInvalidSuite is currently implemented to avoid this
      _ <- check(config.isValidSuite(testClass), {
        val suiteClass = config.getSuiteClass.toTry.get
        val message = if (ScalaPsiUtil.isInheritorDeep(testClass, suiteClass)) {
          ScalaBundle.message("test.config.no.suite.class.is.found.for.class.in.module", getTestClassPath, getModule.getName)
        } else {
          ScalaBundle.message("test.config.class.is.not.inheritor.of.suite.trait", getTestClassPath)
        }
        configurationException(message)
      })
    } yield ()

  override def getTestMap: Map[String, Set[String]] = {
    if (isDumb) return Map(testClassPath -> Set[String]())
    val clazz = getClassPathClazz
    if (clazz == null) throw executionException(ScalaBundle.message("test.run.config.test.class.not.found", testClassPath))
    if (config.isInvalidSuite(clazz)) throw executionException(ScalaBundle.message("test.config.clazz.is.not.a.valid.test.suite", clazz))
    Map(clazz.qualifiedName -> Set[String]())
  }

  override def apply(form: TestRunConfigurationForm): Unit = {
    super.apply(form)
    testClassPath = form.getTestClassPath
  }

  override protected def apply(data: ClassTestData): Unit = {
    super.apply(data)
    testClassPath = data.testClassPath
  }

  override def copy(config: AbstractTestRunConfiguration): ClassTestData = {
    val data = new ClassTestData(config)
    data.apply(this)
    data
  }

  override def readExternal(element: Element): Unit = {
    super.readExternal(element)
    JdomExternalizerMigrationHelper(element) { helper =>
      helper.migrateString("path")(testClassPath = _)
    }
  }
}

object ClassTestData {

  def apply(config: AbstractTestRunConfiguration, className: String): ClassTestData = {
    val res = new ClassTestData(config)
    res.setTestClassPath(className)
    res
  }
}