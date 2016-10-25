package org.jetbrains.plugins.scala.macroAnnotations

import org.jetbrains.plugins.scala.macroAnnotations.ModCount.ModCount

import scala.annotation.{StaticAnnotation, tailrec}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

/**
  * If you annotate a function with @Cached annotation, the compiler will generate code to cache it.
  *
  * If an annotated function has parameters, one field will be generated (a HashMap).
  * If an annotated function has no parameters, two fields will be generated: result and modCount
  *
  * NOTE !IMPORTANT!: function annotated with @Cached must be on top-most level because generated code generates fields
  * right outside the cached function and if this function is inner it won't work.
  *
  * Author: Svyatoslav Ilinskiy
  * Date: 9/18/15.
  */
class Cached(synchronized: Boolean, modificationCount: ModCount, psiElement: Any) extends StaticAnnotation {
  def macroTransform(annottees: Any*) = macro Cached.cachedImpl
}

object Cached {
  def cachedImpl(c: whitebox.Context)(annottees: c.Tree*): c.Expr[Any] = {
    import CachedMacroUtil._
    import c.universe._
    implicit val x: c.type = c

    def abort(message: String) = c.abort(c.enclosingPosition, message)

    def parameters: (Boolean, ModCount.Value, Tree) = {
      @tailrec
      def modCountParam(modCount: c.universe.Tree): ModCount.Value = modCount match {
        case q"modificationCount = $v" => modCountParam(v)
        case q"ModCount.$v" => ModCount.withName(v.toString)
        case q"$v" => ModCount.withName(v.toString)
      }

      c.prefix.tree match {
        case q"new Cached(..$params)" if params.length == 3 =>
          val synch: Boolean = params.head match {
            case q"synchronized = $v" => c.eval[Boolean](c.Expr(v))
            case q"$v" => c.eval[Boolean](c.Expr(v))
          }
          val modCount: ModCount.Value = modCountParam(params(1))
          val psiElement = params(2)
          (synch, modCount, psiElement)
        case _ => abort("Wrong parameters")
      }
    }

    //annotation parameters
    val (synchronized, modCount, psiElement) = parameters

    annottees.toList match {
      case DefDef(mods, name, tpParams, paramss, retTp, rhs) :: Nil =>
        if (retTp.isEmpty) {
          abort("You must specify return type")
        }
        //generated names
        val cacheVarName = c.freshName(name)
        val modCountVarName = c.freshName(name)
        val mapName = c.freshName(name)
        val cachedFunName = generateTermName("cachedFun")
        val cacheStatsName = generateTermName("cacheStats")
        val keyId = c.freshName(name.toString + "cacheKey")
        val analyzeCaches = analyzeCachesEnabled(c)
        val defdefFQN = thisFunctionFQN(name.toString)

        //DefDef parameters
        val flatParams = paramss.flatten
        val paramNames = flatParams.map(_.name)
        val hasParameters: Boolean = flatParams.nonEmpty

        val analyzeCachesField =
          if(analyzeCaches) q"private val $cacheStatsName = $cacheStatisticsFQN($keyId, $defdefFQN)"
          else EmptyTree
        val fields = if (hasParameters) {
          q"""
            private val $mapName = _root_.com.intellij.util.containers.ContainerUtil.
                newConcurrentMap[(..${flatParams.map(_.tpt)}), ($retTp, _root_.scala.Long)]()

            ..$analyzeCachesField
          """
        } else {
          q"""
            new _root_.scala.volatile()
            private var $cacheVarName: _root_.scala.Option[$retTp] = _root_.scala.None
            new _root_.scala.volatile()
            private var $modCountVarName: _root_.scala.Long = 0L

            ..$analyzeCachesField
          """
        }

        def getValuesFromMap: c.universe.Tree = q"""
            var ($cacheVarName, $modCountVarName) = _root_.scala.Option($mapName.get(..$paramNames)) match {
              case _root_.scala.Some((res, count)) => (_root_.scala.Some(res), count)
              case _ => (_root_.scala.None, 0L)
            }
          """
        def putValuesIntoMap: c.universe.Tree = q"$mapName.put((..$paramNames), ($cacheVarName.get, $modCountVarName))"

        val getValuesIfHasParams =
          if (hasParameters) {
            q"""
              ..$getValuesFromMap
            """
          } else q""

        val functionContents = q"""
            ..$getValuesIfHasParams
            if (cacheHasExpired($cacheVarName, $modCountVarName)) {
              val cacheFunResult = $cachedFunName()
              $cacheVarName = _root_.scala.Some(cacheFunResult)
              $modCountVarName = currModCount
              ..${if (hasParameters) putValuesIntoMap else EmptyTree}
            }
            $cacheVarName.get
          """
        val functionContentsInSynchronizedBlock =
          if (synchronized) { //double checked locking
            q"""
              ..$getValuesIfHasParams
              if (!cacheHasExpired($cacheVarName, $modCountVarName)) {
                return $cacheVarName.get
              }
              synchronized {
                $functionContents
              }
            """
          } else {
            q"""
              $functionContents
            """
          }

        val actualCalculation = transformRhsToAnalyzeCaches(c)(cacheStatsName, retTp, rhs)

        val currModCount = modCount match {
          case ModCount.getBlockModificationCount =>
            q"val currModCount = $cachesUtilFQN.enclosingModificationOwner($psiElement).getModificationCount"
          case ModCount.getOutOfCodeBlockModificationCount =>
            q"val currModCount = $scalaPsiManagerFQN.instance($psiElement.getProject).getModificationCount"
          case _ =>
            q"val currModCount = $psiElement.getManager.getModificationTracker.${TermName(modCount.toString)}"
        }
        val updatedRhs = q"""
          def $cachedFunName(): $retTp = {
            if (_root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.isAlreadyGuarded) { $actualCalculation }
            else _root_.org.jetbrains.plugins.scala.util.UIFreezingGuard.withResponsibleUI { $actualCalculation }
          }
          ..$currModCount
          def cacheHasExpired(opt: Option[Any], cacheCount: Long) = opt.isEmpty || currModCount != cacheCount
          ${if (analyzeCaches) q"$cacheStatsName.aboutToEnterCachedArea()" else EmptyTree}
          $functionContentsInSynchronizedBlock
        """
        val updatedDef = DefDef(mods, name, tpParams, paramss, retTp, updatedRhs)
        val res = q"""
          ..$fields
          $updatedDef
          """
        CachedMacroUtil.println(res)
        c.Expr(res)
      case _ => abort("You can only annotate one function!")
    }
  }
}
