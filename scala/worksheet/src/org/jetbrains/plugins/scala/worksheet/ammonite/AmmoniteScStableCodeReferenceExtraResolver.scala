package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceExtraResolver

final class AmmoniteScStableCodeReferenceExtraResolver extends ScStableCodeReferenceExtraResolver {

  override def acceptsFile(file: ScalaFile): Boolean =
    AmmoniteUtil.isAmmoniteFile(file)

  override def resolve(ref: ScStableCodeReference): Option[PsiNamedElement] = {
    val fsi = AmmoniteUtil.scriptResolveQualifier(ref)
    val obj = fsi.flatMap(AmmoniteUtil.file2Object)
    obj
      .orElse(fsi)
      .orElse(AmmoniteUtil.scriptResolveSbtDependency(ref))
  }
}
