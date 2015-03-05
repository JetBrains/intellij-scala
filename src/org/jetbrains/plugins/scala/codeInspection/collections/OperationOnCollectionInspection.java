package org.jetbrains.plugins.scala.codeInspection.collections;

import java.util.Arrays;

/**
 * Nikolay.Tropin
 * 5/28/13
 */
public abstract class OperationOnCollectionInspection extends OperationOnCollectionInspectionBase {
  public Boolean[] simplificationTypesEnabled = new Boolean[possibleSimplificationTypes().length];

  public OperationOnCollectionInspection() {
    Arrays.fill(simplificationTypesEnabled, true);
  }

  @Override
  public Boolean[] getSimplificationTypesEnabled() {
    return simplificationTypesEnabled;
  }

  @Override
  public void setSimplificationTypesEnabled(Boolean[] values) {
    simplificationTypesEnabled = values;
  }
}
