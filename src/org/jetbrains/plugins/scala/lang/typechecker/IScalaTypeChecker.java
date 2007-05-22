/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.scala.lang.typechecker;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.scala.lang.psi.impl.expressions.IScalaExpression;

/**
 * @author ilyas
 */
public interface IScalaTypeChecker {

  public String getTypeRepresentation(IScalaExpression term);

}
