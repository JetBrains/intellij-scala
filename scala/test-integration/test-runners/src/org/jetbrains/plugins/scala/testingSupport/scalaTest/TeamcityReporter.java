package org.jetbrains.plugins.scala.testingSupport.scalaTest;

/**
 * TODO: move all teamcity-specific reporting logic from test reporters to this class
 * https://www.jetbrains.com/help/teamcity/build-script-interaction-with-teamcity.html#BuildScriptInteractionwithTeamCity-ServiceMessages
 */
public class TeamcityReporter {

    public static void reportMessage(String message) {
        //new line prefix needed cause there can be some user unflushed output
        System.out.println("\n" + message);
    }
}
