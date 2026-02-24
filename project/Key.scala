import sbt.{KeyRanks, SettingKey, taskKey}

import java.time.OffsetDateTime

object Key {
  val buildBranch = SettingKey[String]("buildBranch", "Git branch.").withRank(KeyRanks.Invisible)
  val buildCommit = SettingKey[String]("buildCommit", "Git commit.").withRank(KeyRanks.Invisible)
  val buildNumber = SettingKey[String]("buildNumber", "Project current build version").withRank(KeyRanks.Invisible)
  val buildTime   = taskKey[OffsetDateTime]("Time of this build")
  val modified    = taskKey[Boolean]("Is build has uncommited changes")

}
