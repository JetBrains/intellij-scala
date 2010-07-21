package org.jetbrains.plugins.scala
package lang.resolve.processor

import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.CachesUtil
import lang.resolve.{ResolveTargets, ScalaResolveResult}
import scala.collection.Set
import lang.psi.api.statements.{ScTypeAliasDefinition, ScTypeAliasDeclaration, ScTypeAlias}
import lang.psi.types.result.TypingContext
import lang.psi.types.ScType

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.04.2010
 */

class ConstructorResolveProcessor(constr: PsiElement, refName: String, args: List[Seq[Expression]],
                                  typeArgs: Seq[ScTypeElement], kinds: Set[ResolveTargets.Value])
        extends MethodResolveProcessor(constr, refName, args, typeArgs, kinds) {
  override def execute(element: PsiElement, state: ResolveState): Boolean = {
    val named = element.asInstanceOf[PsiNamedElement]
    val subst = getSubst(state)
    if (nameAndKindMatch(named, state)) {
      if (!isAccessible(named, ref)) return true
      named match {
        case clazz: PsiClass => {
          val constructors: Array[PsiMethod] = clazz.getConstructors.filter(isAccessible(_, ref))
          if (constructors.isEmpty) {
            //this is for Traits for example. They can be in constructor position.
            // But they haven't constructors.
            addResult(new ScalaResolveResult(clazz, subst, getImports(state),
              boundClass = getBoundClass(state)))
          }
          else {
            for (constr <- constructors) {
              addResult(new ScalaResolveResult(constr, subst, getImports(state),
                boundClass = getBoundClass(state), parentElement = Some(clazz)))
            }
          }
        }
        case ta: ScTypeAliasDeclaration => {
          addResult(new ScalaResolveResult(ta, subst, getImports(state), boundClass = getBoundClass(state)))
        }
        case ta: ScTypeAliasDefinition => {
          lazy val r = new ScalaResolveResult(ta, subst, getImports(state), boundClass = getBoundClass(state))
          val tp = ta.aliasedType(TypingContext.empty).getOrElse({
            addResult(r)
            return true
          })
          ScType.extractClassType(tp) match {
            case Some((clazz, s)) => {
              val constructors: Array[PsiMethod] = clazz.getConstructors.filter(isAccessible(_, ref))
              if (constructors.isEmpty) addResult(r)
              else {
                for (constr <- constructors) {
                  addResult(new ScalaResolveResult(constr, subst.followed(s), getImports(state),
                    boundClass = getBoundClass(state), parentElement = Some(ta)))
                }
              }
            }
            case _ => {
              addResult(r)
            }
          }
        }
        case _ =>
      }
    }
    return true
  }

  override def candidates[T >: ScalaResolveResult : ClassManifest]: Array[T] = {
    val superCandidates = super.candidates
    if (superCandidates.length <= 1) superCandidates.toArray
    else {
      val constr = superCandidates.apply(0)
      Array(new ScalaResolveResult(constr.getActualElement, constr.substitutor,
        constr.importsUsed, boundClass = constr.boundClass))
    }
  }
}