package org.jetbrains.plugins.scala.annotator.intention

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.java.stubs.index.JavaStaticMemberNameIndex
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, PsiMemberExt, SeqExt, TraversableExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexKeyExt
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

class ScalaImportGlobalMemberFix(override val elements: Seq[MemberToImport],
                                         ref: ScReferenceExpression) extends ScalaImportElementFix(ref) {

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction(editor, ref, elements)

  override def isAddUnambiguous: Boolean = false

  override def getText: String = elements match {
    case Seq(element) => ScalaBundle.message("import.with", element.qualifiedName)
    case _            => ScalaBundle.message("import.something")
  }

  override def getFamilyName: String = ScalaBundle.message("import.global.member")
}

object ScalaImportGlobalMemberFix {

  final class Provider extends UnresolvedReferenceFixProvider {
    override def fixesFor(reference: ScReference): Seq[IntentionAction] =
      reference match {
        case refExpr: ScReferenceExpression if !refExpr.isQualified => ScalaImportGlobalMemberFix(refExpr).toSeq
        case _ => Nil
      }
  }

  def apply(ref: ScReferenceExpression): Option[ScalaImportGlobalMemberFix] = {
    val candidates =
      (findJavaCandidates(ref) ++ findScalaCandidates(ref))
        .toSeq
        .distinctBy(_.qualifiedName)

    if (candidates.isEmpty || candidates.size > 30) None
    else Option(new ScalaImportGlobalMemberFix(candidates, ref))
  }

  private def findJavaCandidates(ref: ScReferenceExpression): Iterable[MemberToImport] =
    JavaStaticMemberNameIndex.getInstance().getStaticMembers(ref.refName, ref.getProject, ref.resolveScope)
      .asScala
      .toSeq
      .flatMap {
        case m: PsiMethod if isStable(m) && isAccessible(m, ref) => Some(MemberToImport(m, m.containingClass))
        case _ => None
      }

  private def findScalaCandidates(ref: ScReferenceExpression): Iterable[MemberToImport] =
    ScalaIndexKeys.METHOD_NAME_KEY.elements(ref.refName, ref.resolveScope)(ref.getProject)
      .filterBy[ScFunctionDefinition]
      .filter(f => isAccessible(f, ref) && !isImplicit(f))
      .flatMap(withContainingObjectOrInheritor)

  private def withContainingObjectOrInheritor(f: ScFunctionDefinition): Set[MemberToImport] =
    (f.containingClass.asOptionOf[ScObject] ++: ScalaInheritors.findInheritorObjectsForOwner(f))
      .map(MemberToImport(f, _))

  private def isStable(m: PsiMethod) =
    ScalaPsiUtil.hasStablePath(m)

  private def isAccessible(m: PsiMethod, ref: ScReferenceExpression) =
    ResolveUtils.isAccessible(m, ref)

  private def isImplicit(f: ScFunctionDefinition) =
    ScalaPsiUtil.isImplicit(f: ScModifierListOwner)
}