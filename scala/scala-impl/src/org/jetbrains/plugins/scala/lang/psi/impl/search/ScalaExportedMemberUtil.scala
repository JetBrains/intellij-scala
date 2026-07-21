package org.jetbrains.plugins.scala.lang.psi.impl.search

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ImportAndExportPsiUtils
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.{MixinNodes, TypeDefinitionMembers}
import org.jetbrains.plugins.scala.lang.psi.types.{Signature, TermSignature}

object ScalaExportedMemberUtil {

  /**
   * Describes an explicitly exported member and the members from supertypes that it overrides or implements.
   *
   * For example:
   * {{{
   * trait Base {
   *   def execute(): Unit = ()
   * }
   *
   * trait Mixin {
   *   def run(): Unit = ()
   * }
   *
   * class Exported extends Base {
   *   val delegate: Mixin = new Mixin {}
   *   export delegate.{run => execute}
   * }
   * }}}
   *
   * The result maps the exported member's fields as follows:
   *  - `semantic` is `Mixin.run`, the declaration being exported;
   *  - `superSignatures` contains `Base.execute`, the declaration overridden by the visible exported name.
   *
   * @param semantic the semantic declaration of the delegated member
   * @param superSignatures the supertype declarations overridden or implemented by the exported member
   */
  case class ExportedMemberOverride(
    semantic: PsiNamedElement,
    superSignatures: Seq[TermSignature]
  )

  /**
   * Finds the overriding information for the explicitly exported member whose visible name contains `element`.
   *
   * See code example in [[ExportedMemberOverride]].
   * For this example, when `element` is the visible `execute` identifier in the `{run => execute}`
   * this returns `ExportedMemberOverride(semantic = Mixin.run, superSignatures = Seq(Base.execute))`.
   *
   * The containing type is resolved by the export's visible name. The result keeps the delegated member as the
   * semantic element and returns the super signatures that it overrides or implements. Wildcard exports and
   * exports without a matching source-level super signature are not considered.
   *
   * @param element an identifier inside an explicit export selector or direct export reference
   * @return the exported member and its super signatures, or `None` when `element` is not a matching export name
   */
  def exportedMemberOverrideAt(element: PsiElement): Option[ExportedMemberOverride] = inReadAction {
    val exportAtCaret = explicitExportAt(element)
    exportAtCaret.flatMap { case (exportStmt, visibleName) =>
      findExportedMemberOverride(exportStmt, visibleName)
    }
  }

  private def findExportedMemberOverride(exportStmt: ScExportStmt, visibleName: String): Option[ExportedMemberOverride] = {
    val containingType = PsiTreeUtil.getParentOfType(exportStmt, classOf[ScTemplateDefinition])
    if (containingType == null)
      None
    else
      findExportedMemberOverrideInType(exportStmt, containingType, visibleName)
  }

  private def findExportedMemberOverrideInType(
    exportStmt: ScExportStmt,
    containingType: ScTemplateDefinition,
    visibleName: String
  ): Option[ExportedMemberOverride] = {
    val signatures = TypeDefinitionMembers.getSignatures(containingType)
    val signaturesForName = signatures.forName(visibleName)
    val foundSignature = signaturesForName.nodesIterator.find(isExportedMemberOverride(_, exportStmt))
    foundSignature.map(toExportedMemberOverride)
  }

  private def isExportedMemberOverride(node: MixinNodes.Node[TermSignature], exportStmt: ScExportStmt): Boolean =
    node.isAllowedImplementationSource &&
      node.supers.nonEmpty &&
      isExportedIn(node, exportStmt) &&
      exportStmt.importExprs.exists(isExplicitNamedExport(_, node.info))

  private def isExportedIn(node: MixinNodes.Node[TermSignature], exportStmt: ScExportStmt): Boolean =
    node.info.exportedInfo.exists { info =>
      info.exportedIn.getExportStatements.exists(_ eq exportStmt)
    }

  private def toExportedMemberOverride(node: MixinNodes.Node[TermSignature]): ExportedMemberOverride =
    ExportedMemberOverride(node.info.namedElement, node.supers.map(_.info))

  /**
   * Checks whether an export expression explicitly names `signature` and can therefore be used
   * for exported-member gutter resolution and navigation.
   */
  private def isExplicitNamedExport(importExpr: ScImportExpr, signature: Signature): Boolean = {
    val namedExportMember = importExpr.explicitNamedMembers
    namedExportMember.exists { member =>
      member.visibleName == signature.name &&
        member.reference.isReferenceTo(signature.namedElement)
    }
  }

  /**
   * Finds the explicit named export whose visible name is `element`.
   *
   * Given a PSI element in a direct export or an explicit export selector, this method returns the containing
   * `ScExportStmt` together with the name exposed by that export. It returns `None` for elements outside an export
   * or for the source name of a renamed export.
   *
   * In the inputs below, the trailing `//element=...` comment identifies the exact PSI element passed to this method;
   * `<caret>` only shows the editor position from which that element was obtained.
   *
   * Direct export:
   *
   * Input:
   * {{{
   * export delegate.run<caret> //element=run
   * }}}
   * Output:
   * {{{
   * Some(export delegate.run -> "run")
   * }}}
   *
   * Renamed export:
   *
   * Input:
   * {{{
   * export delegate.{run => execute<caret>} //element=execute
   * }}}
   * Output:
   * {{{
   * Some(export delegate.{run => execute} -> "execute")
   * }}}
   *
   * The source name of a renamed export is not its visible name, so it is not an input accepted by this method:
   *
   * Input:
   * {{{
   * export delegate.{run<caret> => execute} //element=run
   * }}}
   * Output:
   * {{{
   * None
   * }}}
   */
  private def explicitExportAt(element: PsiElement): Option[(ScExportStmt, String)] =
    ImportAndExportPsiUtils.findExplicitExport(element)

  /**
   * Explicit named exports are mapped to their physical export clause only when both
   * the visible name and reference resolution identify the signature's declaration.
   * For example, both `export delegate.run` and
   * `export delegate.{run => execute}` map to `ScExportStmt`, while
   * `export delegate.*` retains the delegated declaration as the compatibility fallback.
   */
  private[search] def navigationTarget(signature: Signature): Option[PsiElement] = {
    val exportedInfo = signature.exportedInfo
    exportedInfo.flatMap { exportedInfo =>
      val exportStatements = exportedInfo.exportedIn.getExportStatements
      exportStatements.find(_.importExprs.exists(isExplicitNamedExport(_, signature)))
    }
  }
}
