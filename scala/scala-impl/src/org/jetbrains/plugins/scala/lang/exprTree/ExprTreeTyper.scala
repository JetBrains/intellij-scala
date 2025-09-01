package org.jetbrains.plugins.scala.lang.exprTree

import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveState, StdKinds}
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor

import scala.annotation.tailrec

//noinspection ScalaWeakerAccess
object ExprTreeTyper {
  def typeExprTree(exprTree: ExprTree, expectedType: Option[ScType])(implicit ctx: TreeContext): TypedExprTree = exprTree match {
    case tree: TypedExprTree => typeExprTree(tree, expectedType)
    case tree: QualifiedRefExprTree => typeExprTree(tree, expectedType)
    case tree: UnqualifiedRefExprTree => typeExprTree(tree, expectedType)
    case tree: CallExprTree => typeExprTree(tree, expectedType)
    case tree: FunctionLiteralExprTree => typeExprTree(tree, expectedType)
    case tree: UnderscoreReferenceExprTree => typeExprTree(tree, expectedType)
  }

  def typeExprTree(typedExprTree: TypedExprTree, expectedType: Option[ScType])
                  (implicit ctx: TreeContext): typedExprTree.type =
    typedExprTree

  def typeExprTree(unqualifiedRefExprTree: UnqualifiedRefExprTree, expectedType: Option[ScType])
                  (implicit ctx: TreeContext): TypedExprTree = {
    // TODO: look into context for local definitions
    val refName = unqualifiedRefExprTree.refName
    val scope = ???
    val resolver = new ResolveProcessor(StdKinds.refExprQualRef, scope, refName)
    @tailrec
    def walkTreeUp(@Nullable place: PsiElement, @Nullable lastParent: PsiElement): Unit = {
      if (place != null) {
        place.processDeclarations(resolver, ScalaResolveState.empty, lastParent, scope)
        walkTreeUp(place.getContext, lastParent)
      }
    }
    walkTreeUp(scope, null)
    ???
  }

  def typeExprTree(qualifiedRefExprTree: QualifiedRefExprTree, expectedType: Option[ScType])
                  (implicit ctx: TreeContext): TypedExprTree ={
    ???
  }

  def typeExprTree(callExprTree: CallExprTree, expectedType: Option[ScType])
                  (implicit ctx: TreeContext): TypedExprTree = {
    ???
  }

  def typeExprTree(functionLiteralExprTree: FunctionLiteralExprTree, expectedType: Option[ScType])
                  (implicit ctx: TreeContext): TypedExprTree = {
    ???
  }

  def typeExprTree(underscroeRefExprTree: UnderscoreReferenceExprTree, expectedType: Option[ScType])
                  (implicit ctx: TreeContext): TypedExprTree = {
    ???
  }
}
