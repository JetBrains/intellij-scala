package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScForBinding, ScGenerator}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.settings.CompilerIndicesSettings

@Service(Array(Service.Level.PROJECT))
final class ScalaFindUsagesConfiguration(project: Project) {
  private var typeDefinitionOptions: ScalaTypeDefinitionFindUsagesOptions = _
  private var memberOptions: ScalaMemberFindUsagesOptions = _
  private var localOptions: ScalaLocalFindUsagesOptions = _
  private var compilerIndicesOptions: CompilerIndicesSettings = _

  reset()

  def getTypeDefinitionOptions: ScalaTypeDefinitionFindUsagesOptions = typeDefinitionOptions
  def getCompilerIndicesOptions: CompilerIndicesSettings = compilerIndicesOptions

  @TestOnly
  def reset(): Unit = {
    typeDefinitionOptions = new ScalaTypeDefinitionFindUsagesOptions(project)
    memberOptions = new ScalaMemberFindUsagesOptions(project)
    localOptions = new ScalaLocalFindUsagesOptions(project)
    compilerIndicesOptions = CompilerIndicesSettings(project)
  }

  def getMemberOptions: ScalaMemberFindUsagesOptions = memberOptions

  def getFindUsagesOptions(element: PsiElement): Option[FindUsagesOptions] =
    element match {
      case _: ScTypeDefinition =>
        Some(typeDefinitionOptions)
      case inNameContext(m: ScMember) if !m.isLocal =>
        Some(memberOptions)
      case _: ScParameter | _: ScTypeParam |
           inNameContext(_: ScMember | _: ScCaseClause | _: ScGenerator | _: ScForBinding) =>
        Some(localOptions)
      case _ =>
        None
    }
}

object ScalaFindUsagesConfiguration {
  def getInstance(project: Project): ScalaFindUsagesConfiguration = project.getService(classOf[ScalaFindUsagesConfiguration])
}
