package org.jetbrains.plugins.scala.testcases;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.ScalaLoader;

public abstract class ScalaFileSetTestCase extends FileSetTestCase {
  protected Project project;
  private IdeaProjectTestFixture fixture;
  @NonNls
  protected final static String TEMP_FILE = "temp.scala";


  public ScalaFileSetTestCase(String path) {
    super(path);
  }

  protected void setUp() {
    super.setUp();

    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder();
    fixture = fixtureBuilder.getFixture();

    try {
      fixture.setUp();
    } catch (Exception e) {
      //ignore
    }

    project = fixture.getProject();
    ScalaLoader.loadScala();
  }

  protected void tearDown() {
    try {
      fixture.tearDown();
    } catch (Exception e) {
      //ignore
    }
    super.tearDown();
  }

}
