package org.jetbrains.plugins.scala.bazel

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.execution.PsiLocation

import org.jetbrains.bazel.ui.gutters.BazelRunLineMarkerContributor
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestConfigurationProducer

import java.util

class BazelTestRunLineMarkerContributor extends BazelRunLineMarkerContributor {
  private val TEST_ARG = "-t"

  override def isDumbAware: Boolean = true

  override def shouldAddMarker(psiElement: PsiElement): Boolean =
    psiElement match {
      case leaf: LeafPsiElement if leaf.getElementType == ScalaTokenTypes.tIDENTIFIER =>
        leaf.getParent match {
          case _: ScTypeDefinition | _: ScReferenceExpression => true
          case _ => false
        }
      case _ =>
        false
    }

  override def getSingleTestFilter(element: PsiElement): String =
    Option(PsiTreeUtil.getParentOfType(element, classOf[ScClass]))
      .map(_.qualifiedName).orNull

  override def getExtraProgramArguments(psiElement: PsiElement): util.List[String] = {
    val testElement = psiElement.getParent
    val empty: util.List[String] = util.List.of()
    testElement match {
      case _: ScClass => empty
      case _: ScFunctionDefinition => getTestName(testElement)
      case _ if testElement.getParent.is[ScInfixExpr] =>
        val expr = testElement.getParent.asInstanceOf[ScInfixExpr]
        if (expr.operation.equals(testElement)) {
          getTestName(expr)
        } else {
          empty
        }
      case _: ScReferenceExpression if testElement.getParent.is[ScMethodCall] => getTestName(testElement)
      case _ => empty
    }
  }

  private def getTestName(psiElement: PsiElement): util.List[String] = {
    val name =
      for {
        testClass <- Option(PsiTreeUtil.getParentOfType(psiElement, classOf[ScClass]))
        location <- Option(PsiLocation.fromPsiElement(testClass.getProject, psiElement))
        testClassWithTestName <- ScalaTestConfigurationProducer.apply().getTestClassWithTestName(location)
        testName <- testClassWithTestName.testName
      } yield escape(testName)
    name.getOrElse(util.List.of[String]())
  }

  private def escape(testName: String): util.List[String] =
    util.List.of(
      testName.split("\n")
        // Scalatest names can contain spaces, so the name needs to be quoted
        // This means we need to escape " in the test name
        .map(name => name.replace("\"", "\\\""))
        .map(name => s"$TEST_ARG \"$name\"")
        .mkString(" ")
    )
}
