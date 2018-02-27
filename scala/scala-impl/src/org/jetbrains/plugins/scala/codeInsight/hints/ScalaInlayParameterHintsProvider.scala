package org.jetbrains.plugins.scala
package codeInsight
package hints

import java.{util => ju}

import com.intellij.codeInsight.hints._
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiElement

import scala.collection.JavaConverters

class ScalaInlayParameterHintsProvider extends InlayParameterHintsProvider {

  import ScalaInlayParameterHintsProvider._

  import JavaConverters._

  override def getSupportedOptions: ju.List[Option] =
    HintTypes.map(_.option).asJava

  override def getParameterHints(element: PsiElement): ju.List[InlayInfo] =
    parameterHints(element).asJava

  override def getHintInfo(element: PsiElement): HintInfo =
    hintInfo(element).orNull

  override def getInlayPresentation(inlayText: String): String = inlayText

  override val getDefaultBlackList: ju.Set[String] = ju.Collections.singleton("scala.*")

  override def getBlackListDependencyLanguage: JavaLanguage = JavaLanguage.INSTANCE
}

object ScalaInlayParameterHintsProvider {

  import hintTypes._

  private val HintTypes = List(
    ParameterHintType,
    ReturnTypeHintType,
    PropertyHintType,
    LocalVariableHintType
  )

  private def parameterHints(element: PsiElement) =
    HintTypes.flatMap { hintType =>
      hintType(element)
    }

  private def hintInfo(element: PsiElement) =
    HintTypes.find(_.isDefinedAt(element)).collect {
      case ParameterHintType => ParameterHintType.methodInfo(element)
      case hintType => new HintInfo.OptionInfo(hintType.option)
    }
}