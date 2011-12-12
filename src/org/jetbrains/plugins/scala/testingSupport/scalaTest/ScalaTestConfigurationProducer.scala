package org.jetbrains.plugins.scala.testingSupport.scalaTest

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.testingSupport.RuntimeConfigurationProducerAdapter
import org.jetbrains.plugins.scala.ScalaBundle
import com.intellij.execution._
import com.intellij.psi.util.PsiTreeUtil
import configurations.RunConfiguration
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.testingSupport.scalaTest.ScalaTestRunConfigurationForm.TestKind
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr, MethodInvocation, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScFunction}

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.05.2009
 */
class ScalaTestConfigurationProducer extends {
  val confType = new ScalaTestConfigurationType
  val confFactory = confType.confFactory
} with RuntimeConfigurationProducerAdapter(confType) {
  private var myPsiElement: PsiElement = null
  def getSourceElement: PsiElement = myPsiElement

  protected def createConfigurationByElement(location: Location[_ <: PsiElement],
                                                       context: ConfigurationContext): RunnerAndConfigurationSettingsImpl = {
    val scope: GlobalSearchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(context.getModule, true)
    if (ScalaPsiManager.instance(context.getProject).getCachedClass(scope, "org.scalatest.Suite") == null) return null
    myPsiElement = location.getPsiElement
    createConfigurationByLocation(location).asInstanceOf[RunnerAndConfigurationSettingsImpl]
  }

  protected override def findExistingByElement(location: Location[_ <: PsiElement],
                                               existingConfigurations: Array[RunnerAndConfigurationSettings],
                                               context: ConfigurationContext): RunnerAndConfigurationSettings = {
    existingConfigurations.find(c => isConfigurationByLocation(c.getConfiguration, location)).getOrElse(null)
  }

  private def isInheritor(clazz: ScTypeDefinition, fqn: String): Boolean = {
    val suiteClazz = ScalaPsiManager.instance(clazz.getProject).getCachedClass(clazz.getResolveScope, fqn)
    if (suiteClazz == null) return false
    ScalaPsiUtil.cachedDeepIsInheritor(clazz, suiteClazz)
  }
  
  private def getLocationClassAndTest(location: Location[_ <: PsiElement]): (String, String) = {
    val element = location.getPsiElement
    var clazz: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    if (clazz == null) return null
    while (PsiTreeUtil.getParentOfType(clazz, classOf[ScTypeDefinition], true) != null) {
      clazz = PsiTreeUtil.getParentOfType(clazz, classOf[ScTypeDefinition], true)
    }
    if (!clazz.isInstanceOf[ScClass]) return (null, null)
    if (clazz.hasModifierProperty("abstract")) return (null, null)
    if (!isInheritor(clazz, "org.scalatest.Suite")) return (null, null)
    val testClassPath = clazz.getQualifiedName

    def checkCallGeneral(call: MethodInvocation, namesSet: Set[String], fqn: String, 
                         inv: MethodInvocation => Option[(MethodInvocation, String)], 
                         recursive: Boolean): Option[(MethodInvocation, String)] = {
      if (call == null) return None
      call.getInvokedExpr match {
        case ref: ScReferenceExpression if namesSet.contains(ref.refName) =>
          val resolve = ref.resolve()
          if (resolve != null) {
            resolve match {
              case fun: ScFunction =>
                val containingClass = fun.getContainingClass
                if (containingClass != null && containingClass.getQualifiedName == fqn) {
                  val res = inv(call)
                  if (res.isDefined) return res
                }
              case _ =>
            }
          }
        case call: MethodInvocation =>
          checkCallGeneral(call, namesSet, fqn, inv, false) match {
            case Some(res) => return Some(res)
            case _ =>
          }
        case _ =>
      }
      if (!recursive) return None
      checkCallGeneral(PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true), namesSet, fqn, inv, true)
    }

    def endupWithLitral(literal: ScExpression, call: MethodInvocation): Option[(MethodInvocation, String)] = {
      literal match {
        case l: ScLiteral if l.isString =>
          Some((call, l.getValue.asInstanceOf[String]))
        case _ => None
      }
    }

    def checkCall(call: MethodInvocation, namesSet: Set[String], fqn: String) = {
      val inv = (call: MethodInvocation) => {
        val literal = call.argumentExpressions.apply(0)
        endupWithLitral(literal, call)
      }
      checkCallGeneral(call, namesSet, fqn, inv, true)
    }

    def checkInfix(call: MethodInvocation, namesSet: Set[String], fqn: String) = {
      val inv: (MethodInvocation) => Option[(MethodInvocation, String)] = {
        case i: ScInfixExpr =>
          endupWithLitral(i.getBaseExpr, i)
        case call: MethodInvocation =>
          call.getInvokedExpr match {
            case ref: ScReferenceExpression =>
              ref.qualifier match {
                case Some(qual) => endupWithLitral(qual, call)
                case _ => None
              }
            case _ => None
          }
      }
      checkCallGeneral(call, namesSet, fqn, inv, true)
    }

    def checkFunSuite(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      checkCall(PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false), Set("test", "ignore"), fqn) match {
        case Some((_, testName)) => return Some(testName)
        case None =>
      }
      None
    }

    def checkFeatureSpec(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      checkCall(PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false), Set("scenario", "ignore"), fqn) match {
        case Some((call, _testName)) =>
          var testName = "Scenario: " + _testName
          checkCall(PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true), Set("feature"), fqn) match {
            case Some((_, featureName)) =>
              testName = featureName + " " + testName
            case _ =>
          }
          return Some(testName)
        case None =>
      }
      None
    }
    
    def checkFreeSpec(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      def checkFreeSpecInner(innerClassName: String): Option[String] = {
        checkInfix(PsiTreeUtil.getParentOfType(element, classOf[MethodInvocation], false), Set("in", "is", "ignore"),
          fqn + innerClassName) match {
          case Some((_call, _testName)) =>
            var testName = _testName
            var call = _call
            while (call != null) {
              checkInfix(PsiTreeUtil.getParentOfType(call, classOf[MethodInvocation], true), Set("-"),
                fqn + innerClassName) match {
                case Some((_call, _testName)) =>
                  call = _call
                  testName = _testName + " " + testName
                case None => call = null
              }
            }
            Some(testName)
          case None => None
        }
      }
      checkFreeSpecInner(".FreeSpecStringWrapper") match {
        case Some(name) => Some(name)
        case None => checkFreeSpecInner(".ResultOfTaggedAsInvocationOnString")
      }
    }
    
    def checkJUnit3Suite(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      var fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition], false)
      while (fun != null) {
        if (fun.getParent.isInstanceOf[ScTemplateBody] && fun.getContainingClass == clazz) {
          if (fun.getName.startsWith("test")) {
            return Some(fun.getName)
          }
        }
        fun = PsiTreeUtil.getParentOfType(fun, classOf[ScFunctionDefinition], true)
      }
      None
    }

    def checkJUnitSuite(fqn: String): Option[String] = {
      if (!isInheritor(clazz, fqn)) return None
      var fun = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition], false)
      while (fun != null) {
        if (fun.getParent.isInstanceOf[ScTemplateBody] && fun.getContainingClass == clazz) {
          if (fun.hasAnnotation("org.junit.Test") != None) {
            return Some(fun.getName)
          }
        }
        fun = PsiTreeUtil.getParentOfType(fun, classOf[ScFunctionDefinition], true)
      }
      None
    }
    
    class OptionExtension(x: Option[String]) {
      def ++(s: => Option[String]): Option[String] = {
        if (x.isDefined) x
        else s
      }
    }
    implicit def o2e(x: Option[String]): OptionExtension = new OptionExtension(x)


    (testClassPath, checkFunSuite("org.scalatest.FunSuite") ++
      checkFunSuite("org.scalatest.fixture.FixtureFunSuite") ++
      checkFeatureSpec("org.scalatest.FeatureSpec") ++
      checkFeatureSpec("org.scalatest.fixture.FixtureFeatureSpec") ++
      checkFreeSpec("org.scalatest.FreeSpec") ++
      checkFreeSpec("org.scalatest.fixture.FixtureFreeSpec") ++
      checkJUnit3Suite("org.scalatest.junit.JUnit3Suite") ++
      checkJUnitSuite("org.scalatest.junit.JUnitSuite")
      getOrElse null)
  }

  private def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val element = location.getPsiElement
    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      val pack: PsiPackage = element match {
        case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
        case pack: PsiPackage => pack
      }
      if (pack == null) return null
      val displayName = ScalaBundle.message("test.in.scope.scalatest.presentable.text", pack.getQualifiedName)
      val settings = RunManager.getInstance(location.getProject).createRunConfiguration(displayName, confFactory)
      val configuration = settings.getConfiguration.asInstanceOf[ScalaTestRunConfiguration]
      configuration.setTestPackagePath(pack.getQualifiedName)
      configuration.setTestKind(ScalaTestRunConfigurationForm.TestKind.ALL_IN_PACKAGE)
      configuration.setGeneratedName(displayName)
      JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(configuration, location)
      return settings
    }
    val (testClassPath, testClassName) = getLocationClassAndTest(location)
    if (testClassPath == null) return null
    val settings = RunManager.getInstance(location.getProject).
      createRunConfiguration(StringUtil.getShortName(testClassPath) +
      (if (testClassName != null) "." + testClassName else ""), confFactory)
    val runConfiguration = settings.getConfiguration.asInstanceOf[ScalaTestRunConfiguration]
    runConfiguration.setTestClassPath(testClassPath)
    if (testClassName != null) runConfiguration.setTestName(testClassName)
    val kind = if (testClassName == null) TestKind.CLASS else TestKind.TEST_NAME
    runConfiguration.setTestKind(kind)
    try {
      val module = ScalaPsiUtil.getModule(element)
      if (module != null) {
        runConfiguration.setModule(module)
      }
    }
    catch {
      case e =>
    }
    JavaRunConfigurationExtensionManager.getInstance.extendCreatedConfiguration(runConfiguration, location)
    settings
  }

  private def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    val element = location.getPsiElement
    if (element.isInstanceOf[PsiPackage] || element.isInstanceOf[PsiDirectory]) {
      val pack: PsiPackage = element match {
        case dir: PsiDirectory => JavaDirectoryService.getInstance.getPackage(dir)
        case pack: PsiPackage => pack
      }
      if (pack == null) return false
      configuration match {
        case configuration: ScalaTestRunConfiguration => {
          return configuration.getTestKind() == ScalaTestRunConfigurationForm.TestKind.ALL_IN_PACKAGE &&
            configuration.getTestPackagePath == pack.getQualifiedName
        }
        case _ => return false
      }
    }
    val (testClassPath, testClassName) = getLocationClassAndTest(location)
    if (testClassPath == null) return false
    configuration match {
      case configuration: ScalaTestRunConfiguration if configuration.getTestKind() == TestKind.CLASS &&
        testClassName == null =>
        testClassPath == configuration.getTestClassPath
      case configuration: ScalaTestRunConfiguration if configuration.getTestKind() == TestKind.TEST_NAME =>
        testClassPath == configuration.getTestClassPath && testClassName != null &&
          testClassName == configuration.getTestName()
      case _ => false
    }
  }

}