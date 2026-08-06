package org.jetbrains.plugins.scala.bazel

import com.intellij.execution.PsiLocation
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{&, Parent}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScDerivesClauseOwner, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.specs2.{Specs2BazelTestFilter, Specs2TestFramework}

/**
 * Utilities inside contain logic for running entire test classes and individual tests from ScalaTest and ZIO-test via Bazel.
 *
 * @see [[org.jetbrains.plugins.scala.testingSupport.test.ui.ScalaTestRunLineMarkerProvider]] for the rest of Scala test frameworks handling
 * @note This class intentionally does not extend [[org.jetbrains.bazel.ui.gutters.BazelRunLineMarkerContributor]]
 *       to keep a single implementation in [[BazelScalaRunLineMarkerContributor]] because this is the implied way by Bazel plugin.<br>
 *       Still, it's convenient to keep all test-related logic in a separate place.
 */
private object BazelScalaTestRunLineMarkerLogic {
  private val TestArg = "-t"

  def shouldAddMarker(psiElement: PsiElement): Boolean =
    isTestClassOrMethod(psiElement)

  private def isTestClassOrMethod(psiElement: PsiElement): Boolean =
    psiElement match {
      case leaf: LeafPsiElement if leaf.getElementType == ScalaTokenTypes.tIDENTIFIER =>
        leaf.getParent match {
          // only handle ScTypeDefinition (for test class names) and ScReferenceExpression (for individual test names)
          case _: ScTypeDefinition | _: ScReferenceExpression => true
          case _ => false
        }
      case _ =>
        false
    }

  def getSingleTestFilter(psiElement: PsiElement): String =
    getTestClass(psiElement) match {
      case Some(clazz: ScTypeDefinition) if isSpecs2TestClass(clazz) =>
        Specs2BazelTestFilter
          .getContainingTestExprOrScope(psiElement)
          .flatMap(infix => Specs2BazelTestFilter.getTestFilter(clazz, infix))
          .getOrElse(s"${clazz.qualifiedName}.*")
      case Some(clazz) =>
        clazz.qualifiedName
      case None =>
        null
    }

  private def isSpecs2TestClass(clazz: ScTypeDefinition): Boolean =
    Specs2TestFramework().isTestClass(clazz, canBePotential = false)

  private def getTestClass(psiElement: PsiElement): Option[ScDerivesClauseOwner] = {
    val parentClassOfObject = PsiTreeUtil.getParentOfType(psiElement, classOf[ScClass], classOf[ScObject])
    Option(parentClassOfObject)
  }

  /**
   * To run individual tests like:<br>
   * `bazel test --test_filter=MyTestSuite --test_arg=-t --test_arg="test name"`
   */
  def getExtraProgramArguments(psiElement: PsiElement): Seq[String] = {
    val testElement = psiElement.getParent
    getTestName(testElement) match {
      case Some(testName) => Seq(TestArg, testName)
      case None => Seq.empty
    }
  }

  private def getTestName(testElement: PsiElement): Option[String] = testElement match {
    case _: ScClass =>
      None
    case f: ScFunctionDefinition =>
      getTestNameImpl(f)
    case Parent(infix: ScInfixExpr) =>
      if (infix.operation.equals(testElement))
        getTestNameImpl(infix)
      else
        None
    case (_: ScReferenceExpression) & Parent(_: ScMethodCall) =>
      getTestNameImpl(testElement)
    case _ =>
      None
  }

  private def getTestNameImpl(psiElement: PsiElement): Option[String] = {
    val scalaTestName = getScalaTestTestName(psiElement)
    scalaTestName.orElse(getZioTestTestName(psiElement))
  }

  private def getScalaTestTestName(psiElement: PsiElement): Option[String] =
    for {
      testClass <- getTestClass(psiElement)
      location <- Option(PsiLocation.fromPsiElement(testClass.getProject, psiElement))
      testClassWithTestName <- ScalaTestConfigurationProducer.apply().getTestClassWithTestName(location)
      testName <- testClassWithTestName.testName
    } yield testName

  /**
   * borrowed from zio-intellij plugin
   *
   * @see https://github.com/zio/zio-intellij/blob/idea252.x/src/main/scala/zio/intellij/testsupport/package.scala#L34-L45
   */
  private def getZioTestTestName(psiElement: PsiElement): Option[String] =
    psiElement.getParent match {
      case m: ScMethodCall =>
        m.argumentExpressions.headOption.flatMap {
          case lit: ScLiteral => Option(lit.getValue).map(_.toString)
          case _ => None
        }
      case _ => None
    }
}
