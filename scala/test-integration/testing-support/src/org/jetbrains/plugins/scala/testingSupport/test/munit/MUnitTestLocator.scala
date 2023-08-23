package org.jetbrains.plugins.scala.testingSupport.test.munit

import com.intellij.execution.Location
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.extensions.{IteratorExt, OptionExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.testingSupport.locationProvider.PsiLocationWithName
import org.jetbrains.plugins.scala.testingSupport.test.munit.MUnitTestLocator._

import java.util
import java.util.Collections
import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 * @inheritdoc
 */
final class MUnitTestLocator extends SMTestLocator {
  private val javaLocator = new JavaTestLocator()

  override def getLocation(
    protocol: String,
    path: String,
    project: Project,
    scope: GlobalSearchScope
  ): util.List[Location[_ <: PsiElement]] =
    protocol match {
      case JavaTestLocator.SUITE_PROTOCOL =>
        // delegate GoTo for test class to java implementation, cause it already works fine
        javaLocator.getLocation(protocol, path, project, scope)
      case JavaTestLocator.TEST_PROTOCOL  =>
        val maybeLocation = findTestLocationByStaticTestName(path, project, scope)
        maybeLocation.toList.asJava
      case _                              =>
        Collections.emptyList
    }
}

object MUnitTestLocator {

  private val ProtocolPrefix = JavaTestLocator.TEST_PROTOCOL + "://"
  private val ClassWithTestPattern = "(.*?)/(.*?)".r

  def isTestUrl(url: String): Boolean =
    url.startsWith(ProtocolPrefix)

  def getClassFqn(url: String): Option[String] =
    parseLocationUrl(url.stripPrefix(ProtocolPrefix)).map(_._1)

  private def parseLocationUrl(url: String): Option[(String, String)] =
    url.stripPrefix(ProtocolPrefix) match {
      case ClassWithTestPattern(className, testName) => Some((className, testName))
      case _                                  => None
    }

  private def findTestLocationByStaticTestName(
    testLocationUrl: String,
    project: Project,
    scope: GlobalSearchScope
  ): Option[Location[_ <: PsiElement]] =
    parseLocationUrl(testLocationUrl) match {
      case Some((clazzName, testName)) =>
        findTestLocationByStaticTestName(clazzName, testName, project, scope)
      case _ =>
        None
    }

  private def findTestLocationByStaticTestName(
    clazzName: String,
    testName: String,
    project: Project,
    scope: GlobalSearchScope
  ): Option[Location[_ <: PsiElement]] = {
    val clazzOpt = ScalaPsiManager.instance(project).getCachedClass(scope, clazzName)
    val templateBody = clazzOpt.filterByType[ScClass].flatMap(_.extendsBlock.templateBody)
    templateBody.flatMap(findTestLocationByStaticTestName(_, testName))
  }

  private def findTestLocationByStaticTestName(
    templateBody: ScTemplateBody,
    testName: String,
  ): Option[Location[_ <: PsiElement]] = {
    val methodCalls = templateBody.children.filterByType[ScMethodCall].toArray // TODO
    val testMethodCalls = methodCalls.flatMap(testRefWithTestName)
    val found = testMethodCalls.find(_._2 == testName)
    found.map(_._1).map(PsiLocationWithName(_, testName))
  }

  private def testRefWithTestName(methodCall: ScMethodCall): Option[(ScReferenceExpression, String)]=
    methodCall.deepestInvokedExpr match {
      case testRef: ScReferenceExpression if MUnitUtils.FunSuiteTestMethodNames.exists(testRef.textMatches) =>
        val maybeStaticName = MUnitUtils.staticTestName(testRef)
        maybeStaticName.map((testRef, _))
      case _ => None
    }
}