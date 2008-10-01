package org.jetbrains.plugins.scala.lang.psi.impl.compiled
import com.intellij.lang.Language
import com.intellij.psi.impl.compiled.ClsRepositoryPsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import wrappers.ScClsElementWrapperImpl

/**
 * @author ilyas
 */

abstract class ScClsElementImpl[S <: PsiElement, T <: StubElement[S]](stub: T) extends ScClsElementWrapperImpl[S, T](stub) {

  override def getLanguage: Language = ScalaFileType.SCALA_LANGUAGE
}