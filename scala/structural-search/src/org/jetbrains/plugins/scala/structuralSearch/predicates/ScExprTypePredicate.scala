package org.jetbrains.plugins.scala.structuralSearch.predicates

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.structuralsearch.impl.matcher.MatchContext
import com.intellij.structuralsearch.impl.matcher.predicates.MatchPredicate
import com.intellij.structuralsearch.{MalformedPatternException, SSRBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResultExt
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScTypeExt}

import java.util.regex.{Pattern, PatternSyntaxException}

class ScExprTypePredicate(val ty: String, baseName: String, val withinHierarchy: Boolean, val inverted: Boolean, val caseSensitive: Boolean, val isRegex: Boolean) extends MatchPredicate {
  private val pattern: Option[Pattern] = if isRegex then {
    try {
      Some(Pattern.compile(ty, (if caseSensitive then 0 else Pattern.CASE_INSENSITIVE)))
    } catch {
      case _: PatternSyntaxException =>
        throw new MalformedPatternException(SSRBundle.message("error.incorrect.regexp.constraint", ty, baseName))
      case _ => None
    }
  } else None
  private val types = ty.split('|').map(_.replace("\\.", "."))

  override def `match`(matchedNode: PsiElement, start: Int, end: Int, context: MatchContext): Boolean = {
    matchedNode match {
      case expr: ScExpression =>
        val typ = (expr.`type`().getOrAny match {
          case typ: ScLiteralType => typ.wideType
          case typ => typ
        }).unpackedType
        typ.extractClass match {
          case None => matchName(typ.toString)
          case Some(cl) =>
            if (withinHierarchy) {
              matchSupers(cl, matchClass)
            } else {
              matchClass(cl)
            }
        }
      case _: LeafPsiElement => matchedNode.getParent match {
        case td: ScTypeDefinition =>
          if (withinHierarchy) {
            matchSupers(td, matchClass)
          } else {
            matchClass(td)
          }
        case _ => false
      }
      case td: ScTypeDefinition =>
        if (withinHierarchy) {
          matchSupers(td, matchClass)
        } else {
          matchClass(td)
        }
      case _ => false
    }
  }

  private def matchClass(cl: PsiClass): Boolean = {
    (cl.getName match {
      case null => false
      case n => matchName(n)
    }) || (cl.getQualifiedName match {
      case null => false
      case n => matchName(n)
    })
  }

  private def matchName(name: String): Boolean = {
    if (isRegex) {
      pattern.exists(_.matcher(name).matches())
    } else {
      types.exists(ty => if caseSensitive then name == ty else name.equalsIgnoreCase(ty)) ^ inverted
    }
  }

  private def matchSupers(cl: PsiClass, matcher: PsiClass => Boolean): Boolean = {
    def matchSupers(cl: PsiClass): Boolean = {
      if (matcher(cl)) return true
      cl.getSupers.exists(matchSupers)
    }
    matchSupers(cl)
  }
}
