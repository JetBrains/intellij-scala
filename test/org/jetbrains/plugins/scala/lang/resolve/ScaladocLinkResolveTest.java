package org.jetbrains.plugins.scala.lang.resolve;

import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl;
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScTypeParamImpl;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * User: Dmitry Naydanov
 * Date: 12/5/11
 */
public class ScaladocLinkResolveTest extends ScaladocLinkResolveBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp(TestUtils.ScalaSdkVersion._2_10);
  }

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
