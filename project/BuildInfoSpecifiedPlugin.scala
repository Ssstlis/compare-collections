import Key.*
import com.typesafe.sbt.GitPlugin
import com.typesafe.sbt.SbtGit.git
import sbt.*
import sbt.Keys.*
import sbtbuildinfo.BuildInfoKey
import sbtbuildinfo.BuildInfoKeys.{buildInfoKeys, buildInfoPackage}
import sbtbuildinfo.BuildInfoPlugin

import java.time.OffsetDateTime

object BuildInfoSpecifiedPlugin extends AutoPlugin {

  override def requires: Plugins = BuildInfoPlugin && GitPlugin
  override def trigger           = noTrigger

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    buildCommit      := git.gitHeadCommit.value.getOrElse("unknown"),
    buildBranch      := git.gitCurrentBranch.value,
    buildTime        := OffsetDateTime.now(),
    buildNumber      := sys.props.getOrElse("BUILD_NUMBER", "0"),
    modified         := git.gitUncommittedChanges.value,
    buildInfoKeys    := Seq[BuildInfoKey](name, version, buildCommit, buildBranch, buildTime, buildNumber, modified),
    buildInfoPackage := "io.github.ssstlis." + name.value.replace('-', '_')
  )
}
