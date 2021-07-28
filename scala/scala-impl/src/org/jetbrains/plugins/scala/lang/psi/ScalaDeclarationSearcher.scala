package org.jetbrains.plugins.scala.lang.psi

import com.intellij.lang.jvm.JvmElement
import com.intellij.lang.jvm.source.JvmDeclarationSearcher
import com.intellij.psi.PsiElement

import java.util
import java.util.Collections.{emptyList, singletonList}

class ScalaDeclarationSearcher extends JvmDeclarationSearcher {
  override def findDeclarations(declaringElement: PsiElement): util.Collection[JvmElement] =
    declaringElement match {
      case element: JvmElement => singletonList(element)
      case _ => emptyList
    }
}
