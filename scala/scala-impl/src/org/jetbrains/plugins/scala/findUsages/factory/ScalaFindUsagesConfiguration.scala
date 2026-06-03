package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.{AbstractFindUsagesDialog, FindUsagesHandler, FindUsagesOptions}
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.findUsages.factory.ScalaFindUsagesConfiguration.FindUsagesOptionsResolver
import org.jetbrains.plugins.scala.findUsages.factory.dialog.{ScalaOverridableMemberFindUsagesDialog, ScalaTypeDefinitionUsagesDialog}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScContextBound
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

  def getFindUsagesOptionsResolver(element: PsiElement): FindUsagesOptionsResolver =
    element match {
      case typeDef: ScTypeDefinition =>
        FindUsagesOptionsResolver.ForTypeDefinitions(typeDef, typeDefinitionOptions)
      case inNameContext(m: ScMember) if !m.isLocal =>
        FindUsagesOptionsResolver.ForMembers(m, memberOptions)
      case _: ScParameter | _: ScTypeParam | _: ScContextBound =>
        FindUsagesOptionsResolver.ForLocals(localOptions)
      case inNameContext(_: ScMember | _: ScCaseClause | _: ScGenerator | _: ScForBinding) =>
        FindUsagesOptionsResolver.ForLocals(localOptions)
      case _ =>
        FindUsagesOptionsResolver.ForNone
    }
}

object ScalaFindUsagesConfiguration {
  def getInstance(project: Project): ScalaFindUsagesConfiguration = project.getService(classOf[ScalaFindUsagesConfiguration])

  abstract class FindUsagesOptionsResolver {
    def getOptions: Option[FindUsagesOptions]
    def getDialog(handler: FindUsagesHandler,
                  project: Project,
                  isSingleFile: Boolean,
                  toShowInNewTab: Boolean,
                  mustOpenInNewTab: Boolean): Option[AbstractFindUsagesDialog] = None
  }

  object FindUsagesOptionsResolver {
    case class ForTypeDefinitions(typeDef: ScTypeDefinition, options: ScalaTypeDefinitionFindUsagesOptions) extends FindUsagesOptionsResolver {
      override def getOptions: Some[ScalaTypeDefinitionFindUsagesOptions] = Some(options)
      override def getDialog(handler: FindUsagesHandler,
                             project: Project,
                             isSingleFile: Boolean,
                             toShowInNewTab: Boolean,
                             mustOpenInNewTab: Boolean): Option[AbstractFindUsagesDialog] =
        Some(new ScalaTypeDefinitionUsagesDialog(
          typeDef,
          project,
          options,
          toShowInNewTab,
          mustOpenInNewTab,
          isSingleFile,
          handler
        ))
    }

    case class ForMembers(member: ScMember, options: ScalaMemberFindUsagesOptions) extends FindUsagesOptionsResolver {
      override def getOptions: Some[ScalaMemberFindUsagesOptions] = Some(options)
      override def getDialog(handler: FindUsagesHandler,
                             project: Project,
                             isSingleFile: Boolean,
                             toShowInNewTab: Boolean,
                             mustOpenInNewTab: Boolean): Option[AbstractFindUsagesDialog] = {
        Some(new ScalaOverridableMemberFindUsagesDialog(
          member,
          project,
          options,
          toShowInNewTab,
          mustOpenInNewTab,
          isSingleFile,
          handler
        ))
      }
    }

    case class ForLocals(options: ScalaLocalFindUsagesOptions) extends FindUsagesOptionsResolver {
      override def getOptions: Some[ScalaLocalFindUsagesOptions] = Some(options)
    }

    object ForNone extends FindUsagesOptionsResolver {
      override def getOptions: None.type = None
    }
  }
}
