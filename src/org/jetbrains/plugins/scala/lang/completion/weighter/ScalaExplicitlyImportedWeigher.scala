package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.util.ProximityLocation
import com.intellij.psi.util.proximity.ProximityWeigher
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
  * @author Alexander Podkhalyuzin
  */
class ScalaExplicitlyImportedWeigher extends ProximityWeigher {
  def applyQualifier(qual: String, position: PsiElement): Option[Integer] = {
    if (position == null) return None
    val index = qual.lastIndexOf('.')
    val qualNoPoint = if (index < 0) null else qual.substring(0, index)
    val tuple: (ArrayBuffer[ScImportStmt], Long) = position.getUserData(ScalaExplicitlyImportedWeigher.key)
    var buffer: ArrayBuffer[ScImportStmt] = if (tuple != null) tuple._1 else null
    val currentModCount = position.getManager.getModificationTracker.getModificationCount
    if (buffer == null || tuple._2 != currentModCount) {
      @tailrec
      def treeWalkup(place: PsiElement, lastParent: PsiElement) {
        if (place == null) return
        place match {
          case holder: ScImportsHolder =>
            buffer ++= holder.getImportsForLastParent(lastParent)
            if (place.isInstanceOf[ScalaFile]) return
          case _ =>
        }
        treeWalkup(place.getContext, place)
      }
      buffer = new ArrayBuffer[ScImportStmt]()
      treeWalkup(position.getContext, position)
      position.putUserData(ScalaExplicitlyImportedWeigher.key, (buffer, currentModCount))
    }
    val iter = buffer.iterator
    while (iter.hasNext) {
      val stmt = iter.next()
      val exprIter = stmt.importExprs.iterator
      while (exprIter.hasNext) {
        val expr = exprIter.next()
        if (expr.singleWildcard && qualNoPoint != null) {
          for (resolve <- expr.qualifier.multiResolve(false))
            resolve match {
              case ScalaResolveResult(pack: PsiPackage, _) =>
                if (qualNoPoint == pack.getQualifiedName) return Some(3)
              case ScalaResolveResult(clazz: PsiClass, _) =>
                if (qualNoPoint == clazz.qualifiedName) return Some(3)
              case _ =>
            }
        } else if (expr.selectorSet != None) {
          for (selector <- expr.selectors) {
            for (resolve <- selector.reference.multiResolve(false)) {
              resolve match {
                case ScalaResolveResult(clazz: PsiClass, _) =>
                  if (qual == clazz.qualifiedName) return Some(3)
                case _ =>
              }
            }
          }
        } else {
          expr.reference match {
            case Some(ref) =>
              for (resolve <- ref.multiResolve(false))
                resolve match {
                  case ScalaResolveResult(clazz: PsiClass, _) =>
                    if (qual == clazz.qualifiedName) return Some(3)
                  case _ =>
                }
            case None =>
          }
        }
      }
    }
    if (qualNoPoint != null && qualNoPoint == "scala" ||
      qualNoPoint == "java.lang" || qualNoPoint == "scala.Predef") {
      if (qualNoPoint == "java.lang") return Some(1)
      else return Some(2)
    }
    None
  }

  def applyToMember(member: ScMember, position: PsiElement): Option[Integer] = {
    member.getContext match {
      case tb: ScTemplateBody =>
        val clazz: PsiClass = member.containingClass
        clazz match {
          case obj: ScObject =>
            val qualNoPoint = obj.qualifiedName
            if (qualNoPoint != null) {
              val memberName = member match {
                case named: ScNamedElement => named.name
                case _ => member.getName
              }
              val qual = qualNoPoint + "." + memberName
              applyQualifier(qual, position) match {
                case Some(x) => return Some(x)
                case None =>
              }
            }
          case _ =>
        }
      case _ =>
    }
    None
  }

  def weigh(element: PsiElement, location: ProximityLocation): Integer = {
    val position: PsiElement = location.getPosition
    if (position == null) {
      return 0
    }
    val elementFile: PsiFile = element.getContainingFile
    val positionFile: PsiFile = position.getContainingFile
    if (!positionFile.isInstanceOf[ScalaFile]) return 0
    if (positionFile != null && elementFile != null && positionFile.getOriginalFile == elementFile.getOriginalFile) {
      return 3
    }
    element match {
      case clazz: PsiClass if clazz.qualifiedName != null =>
        val qual: String = clazz.qualifiedName
        applyQualifier(qual, position) match {
          case Some(x) => return x
          case None =>
        }
      case member: ScMember =>
        applyToMember(member, position) match {
          case Some(x) => return x
          case None =>
        }
      case member: PsiMember if member.hasModifierProperty("static") =>
        val clazz = member.containingClass
        if (clazz != null && clazz.qualifiedName != null) {
          val qualNoPoint = clazz.qualifiedName
          val memberName = member match {
            case named: ScNamedElement => named.name
            case _ => member.getName
          }
          val qual = qualNoPoint + "." + memberName
          applyQualifier(qual, position) match {
            case Some(x) => return x
            case None =>
          }
        }
      case b: ScBindingPattern =>
        ScalaPsiUtil.nameContext(b) match {
          case v: ScValue =>
            applyToMember(v, position) match {
              case Some(x) => return x
              case None =>
            }
          case v: ScVariable =>
            applyToMember(v, position) match {
              case Some(x) => return x
              case None =>
            }
          case _ =>
        }
      case _ =>
    }
    0
  }
}

object ScalaExplicitlyImportedWeigher {
  private[weighter] val key: Key[(ArrayBuffer[ScImportStmt], Long)] = Key.create("scala.explicitly.imported.weigher.key")
}