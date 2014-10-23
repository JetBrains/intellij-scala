package org.jetbrains.plugins.scala.codeInspection.collections;

import java.util.Arrays;

/**
 * Nikolay.Tropin
 * 5/28/13
 */
public abstract class OperationOnCollectionInspection extends OperationOnCollectionInspectionBase {
  private final String[] likeOptionClassesDefault = {"scala.Option", "scala.Some", "scala.None"};
  private final String[] likeCollectionClassesDefault = {"scala.collection._", "scala.Option"};
  public String[] likeOptionClasses = likeOptionClassesDefault;
  public String[] likeCollectionClasses = likeCollectionClassesDefault;
  public Boolean[] simplificationTypeChecked = new Boolean[possibleSimplificationTypes().length];

  public OperationOnCollectionInspection() {
    Arrays.fill(simplificationTypeChecked, true);
  }


  @Override
  public String[] getLikeCollectionClasses() {
    return likeCollectionClasses;
  }

  @Override
  public String[] getLikeOptionClasses() {
    return likeOptionClasses;
  }

  @Override
  public void setLikeCollectionClasses(String[] values) {
    likeCollectionClasses = values;
  }

  @Override
  public void setLikeOptionClasses(String[] values) {
    likeOptionClasses = values;
  }

  @Override
  public Boolean[] getSimplificationTypeChecked() {
    return simplificationTypeChecked;
  }

  @Override
  public void setSimplificationTypeChecked(Boolean[] values) {
    simplificationTypeChecked = values;
  }
}
