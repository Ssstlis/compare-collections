package io.github.ssstlis.collection_compare.mongo

import com.typesafe.config.{Config, ConfigFactory}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import scala.util.Try

case class HostAndPort(host: String, port: Int = MongoConfig.defaultPort)

case class MongoConfig(
  host: String,
  user: String,
  password: String,
  database: String,
  maxPoolSize: Int,
  waitQueueMultiple: Int,
  port: Int,
  zoneId: String,
  hosts: Seq[HostAndPort]
) {
  def connectionString: String = {
    val hostsPart =
      if (hosts.isEmpty) s"$host:$port"
      else hosts.map(c => s"${c.host}:${c.port}").mkString(",")
    val authPart =
      if (user.nonEmpty) s"${MongoConfig.urlEncode(user)}:${MongoConfig.urlEncode(password)}@"
      else ""
    s"mongodb://$authPart$hostsPart/$database?maxPoolSize=$maxPoolSize&waitQueueMultiple=$waitQueueMultiple"
  }
}

object MongoConfig {
  val defaultPort = 27017
  val defaultKey  = "default"

  /** Loads the named section from configuration file under `mongodb.<key>`. Example: `MongoConfig.load("prod")` reads
    * `mongodb.prod { ... }`. Defaults to `mongodb.default` when called without arguments.
    */
  def load(key: String = defaultKey): MongoConfig =
    apply(ConfigFactory.load().getConfig(s"mongodb.$key"))
      .fold(
        err => throw new RuntimeException(s"mongodb.$key not found in configuration file: ${err.getMessage}", err),
        identity
      )

  def apply(config: Config): Try[MongoConfig] = scala.util.Try {
    MongoConfig(
      host = config.getString("host"),
      user = Try(config.getString("user")).getOrElse(""),
      password = Try(config.getString("password")).getOrElse(""),
      database = config.getString("database"),
      maxPoolSize = Try(config.getInt("maxPoolSize")).getOrElse(10),
      waitQueueMultiple = Try(config.getInt("waitQueueMultiple")).getOrElse(2),
      port = Try(config.getInt("port")).getOrElse(defaultPort),
      zoneId = Try(config.getString("zoneId")).getOrElse(ZoneId.systemDefault().toString),
      hosts = Try {
        val hostConf = config.getConfigList("hosts")
        val itr      = hostConf.iterator()
        val res      = collection.mutable.Map.empty[String, HostAndPort]
        while (itr.hasNext) {
          val next = itr.next()
          val hap  = HostAndPort(next.getString("host"), Try(next.getInt("port")).getOrElse(defaultPort))
          res.put(hap.host, hap)
        }
        res.values.toList
      }.orElse(Try {
        val hostConf = config.getStringList("hosts")
        val port     = Try(config.getInt("port")).getOrElse(defaultPort)
        val itr      = hostConf.iterator()
        val res      = collection.mutable.Map.empty[String, HostAndPort]
        while (itr.hasNext) {
          val next = itr.next()
          val hap  = HostAndPort(next, port)
          res.put(hap.host, hap)
        }
        res.values.toList
      }).getOrElse(Nil)
    )
  }

  def urlEncode(str: String): String =
    URLEncoder.encode(str, StandardCharsets.UTF_8.toString)
}
