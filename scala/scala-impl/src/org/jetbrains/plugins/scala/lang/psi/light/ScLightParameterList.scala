package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.lang.Language
import com.intellij.psi.impl.light.LightParameterListBuilder
import com.intellij.psi.{PsiManager, PsiParameter}

private class ScLightParameterList(manager: PsiManager, language: Language, params: collection.Seq[PsiParameter])
  extends LightParameterListBuilder(manager, language) {

  params.foreach(addParameter)
}