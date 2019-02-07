package org.jetbrains.plugins.scala
package lang
package scaladoc
package psi
package api

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference

/**
 * User: Dmitry Naydanov
 * Date: 11/30/11
 */

trait ScDocResolvableCodeReference extends ScalaPsiElement with ScStableCodeReference