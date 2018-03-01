package org.jetbrains.plugins.scala
package codeInsight
package hints

import java.{util => ju}

import com.intellij.codeInsight.hints._
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiElement

import scala.collection.JavaConverters

class ScalaInlayParameterHintsProvider extends InlayParameterHintsProvider {

  import JavaConverters._

  private val HintTypes = List(ParameterHintType, MemberHintType)

  override def getSupportedOptions: ju.List[Option] =
    HintTypes.flatMap(_.options).asJava

  override def getParameterHints(element: PsiElement): ju.List[InlayInfo] =
    HintTypes.flatMap(_.apply(element)).asJava

  override def getHintInfo(element: PsiElement): HintInfo = element match {
    case ParameterHintType.methodInfo(methodInfo) => methodInfo
    case MemberHintType.hintOption(optionInfo) => optionInfo
    case _ => null
  }

  override def getInlayPresentation(inlayText: String): String = inlayText

  override val getDefaultBlackList: ju.Set[String] = ju.Collections.singleton("scala.*")

  override def getBlackListDependencyLanguage: JavaLanguage = JavaLanguage.INSTANCE
}