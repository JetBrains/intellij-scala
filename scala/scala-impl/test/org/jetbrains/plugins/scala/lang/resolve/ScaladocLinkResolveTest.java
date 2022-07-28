package org.jetbrains.plugins.scala.lang.resolve;

import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl;
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamImpl;

public class ScaladocLinkResolveTest extends ScaladocLinkResolveBase {

  public void testCodeLinkResolve() throws Exception {
    genericResolve(1);
  }

  public void testCodeLinkMultiResolve() throws Exception {
    genericResolve(2);
  }

  public void testMethodParamResolve() throws Exception {
    genericResolve(1, ScParameterImpl.class);
  }

  public void testMethodTypeParamResolve() throws Exception {
    genericResolve(1, ScTypeParamImpl.class);
  }

  public void testMethodParamNoResolve() throws Exception {
    genericResolve(0);
  }

  public void testPrimaryConstrParamResolve() throws Exception {
    genericResolve(1, ScParameterImpl.class);
  }

  public void testPrimaryConstrTypeParamResolve() throws Exception {
    genericResolve(1, ScTypeParamImpl.class);
  }

  public void testPrimaryConstrParamNoResolve() throws Exception {
    genericResolve(0);
  }

  public void testPrimaryConstrTypeParamNoResolve() throws Exception {
    genericResolve(0);
  }

  public void testTypeAliasParamResolve() throws Exception {
    genericResolve(1, ScTypeParamImpl.class);
  }

  public void testTypeAliasParamNoResolve() throws Exception {
    genericResolve(0);
  }
}
