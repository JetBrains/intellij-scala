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
import collection.immutable.HashSet

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.04.2010
 */

class ConstructorResolveProcessor(constr: PsiElement, refName: String, args: List[Seq[Expression]],
                                  typeArgs: Seq[ScTypeElement], kinds: Set[ResolveTargets.Value],
                                  shapeResolve: Boolean)
        extends MethodResolveProcessor(constr, refName, args, typeArgs, kinds,
          isShapeResolve = shapeResolve, enableTupling = true) {
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
            addResult(new ScalaResolveResult(clazz, subst, getImports(state), boundClass = getBoundClass(state),
              fromType = getFromType(state)))
          }
          else {
            for (constr <- constructors) {
              addResult(new ScalaResolveResult(constr, subst, getImports(state), parentElement = Some(clazz),
                boundClass = getBoundClass(state), fromType = getFromType(state)))
            }
          }
        }
        case ta: ScTypeAliasDeclaration => {
          addResult(new ScalaResolveResult(ta, subst, getImports(state), boundClass = getBoundClass(state),
            fromType = getFromType(state)))
        }
        case ta: ScTypeAliasDefinition => {
          lazy val r = new ScalaResolveResult(ta, subst, getImports(state), boundClass = getBoundClass(state),
            fromType = getFromType(state))
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
                    parentElement = Some(ta), boundClass = getBoundClass(state), fromType = getFromType(state)))
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

  override def candidatesS: Set[ScalaResolveResult] = {
    val superCandidates = super.candidatesS
    if (superCandidates.size <= 1) superCandidates
    else {
      superCandidates.map(constr => new ScalaResolveResult(constr.getActualElement, constr.substitutor,
        constr.importsUsed, boundClass = constr.boundClass, fromType = constr.fromType))
    }
  }
}