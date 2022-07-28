package org.jetbrains.plugins.scala.codeInspection.collections;

import java.util.Arrays;

public abstract class OperationOnCollectionInspection extends OperationOnCollectionInspectionBase {
  public Boolean[] simplificationTypesEnabled = new Boolean[possibleSimplificationTypes().size()];

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
