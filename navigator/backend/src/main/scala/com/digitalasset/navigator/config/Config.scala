// Copyright (c) 2019 The DAML Authors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.navigator.config

import java.nio.file.{Files, Path}
import java.nio.file.StandardOpenOption._

import com.digitalasset.assistant.config.{
  ConfigMissing => SdkConfigMissing,
  ConfigLoadError => SdkConfigLoadError,
  ConfigParseError => SdkConfigParseError,
  ProjectConfig
}
import com.digitalasset.navigator.model.PartyState
import com.digitalasset.ledger.api.refinements.ApiTypes
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import org.slf4j.LoggerFactory
import pureconfig.{ConfigConvert, ConfigWriter}
import scalaz.Tag

final case class UserConfig(password: Option[String], party: PartyState, role: Option[String])

/* The configuration has an empty map as default list of users because you can login as party too */
@SuppressWarnings(Array("org.wartremover.warts.Option2Iterable"))
final case class Config(users: Map[String, UserConfig] = Map.empty[String, UserConfig]) {

  def userIds: Set[String] = users.keySet

  def roles: Set[String] = users.values.flatMap(_.role)(collection.breakOut)
  def parties: Set[PartyState] = users.values.map(_.party)(collection.breakOut)
}

sealed abstract class ConfigReadError extends Product with Serializable {
  def reason: String
}

final case class ConfigNotFound(reason: String) extends ConfigReadError
final case class ConfigInvalid(reason: String) extends ConfigReadError
final case class ConfigParseFailed(reason: String) extends ConfigReadError

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Any",
    "org.wartremover.warts.Option2Iterable",
    "org.wartremover.warts.ExplicitImplicitTypes"))
object Config {

  private[this] val logger = LoggerFactory.getLogger(this.getClass)

  def load(configFile: Path, useDatabase: Boolean): Either[ConfigReadError, Config] = {
    loadSdkConfig(useDatabase).left
      .flatMap {
        case ConfigNotFound(_) =>
          logger.warn("SDK config does not exist. Falling back to Navigator config file.")
          loadNavigatorConfig(configFile, useDatabase)
        case e: ConfigReadError =>
          logger.warn(s"SDK config exists, but is not usable: ${e.reason}")
          Left(e)
      }
  }

  def loadNavigatorConfig(
      configFile: Path,
      useDatabase: Boolean): Either[ConfigReadError, Config] = {
    implicit val partyConfigConvert = ConfigConvert.viaNonEmptyString[PartyState](
      str => _ => Right(new PartyState(ApiTypes.Party(str), useDatabase)),
      t => Tag.unwrap(t.name))
    if (Files.exists(configFile)) {
      logger.info(s"Loading Navigator config file from $configFile")
      val config = ConfigFactory.parseFileAnySyntax(configFile.toAbsolutePath.toFile)
      pureconfig
        .loadConfig[Config](config)
        .left
        .map(e => ConfigParseFailed(e.toList.mkString(", ")))
    } else {
      Left(ConfigNotFound(s"File $configFile not found"))
    }

  }

  def loadSdkConfig(useDatabase: Boolean): Either[ConfigReadError, Config] = {
    val partiesE = for {
      projectConfigPath <- ProjectConfig.projectConfigPath()
      _ = logger.info(s"Loading SDK config file from $projectConfigPath")
      projectConfig <- ProjectConfig.loadFromFile(projectConfigPath)
      result <- projectConfig.parties
    } yield result

    partiesE match {
      case Right(Some(parties)) =>
        Right(
          Config(
            parties
              .map(p => p -> UserConfig(None, new PartyState(ApiTypes.Party(p), useDatabase), None))
              .toMap))
      case Right(None) =>
        val message = "Found a SDK project config file, but it does not contain any parties."
        Left(ConfigInvalid(message))
      case Left(SdkConfigMissing(reason)) =>
        Left(ConfigNotFound(reason))
      case Left(SdkConfigParseError(reason)) =>
        val message = s"Found a SDK project config file, but it could not be parsed: $reason."
        Left(ConfigParseFailed(message))
      case Left(SdkConfigLoadError(reason)) =>
        val message = s"Found a SDK project config file, but it could not be loaded: $reason."
        Left(ConfigParseFailed(message))
    }
  }

  def template(useDatabase: Boolean): Config =
    Config(
      Map(
        "OPERATOR" -> UserConfig(
          Some("password"),
          new PartyState(ApiTypes.Party("party"), useDatabase),
          None)
      ))

  def writeTemplateToPath(configFile: Path, useDatabase: Boolean): Unit = {
    implicit val partyConfigConvert = ConfigConvert.viaNonEmptyString[PartyState](
      str => _ => Right(new PartyState(ApiTypes.Party(str), useDatabase)),
      t => Tag.unwrap(t.name))
    val config = ConfigWriter[Config].to(template(useDatabase))
    val cro = ConfigRenderOptions
      .defaults()
      .setComments(false)
      .setOriginComments(false)
      .setFormatted(true)
      .setJson(false)
    Files.write(configFile, config.render(cro).getBytes, CREATE_NEW)
    ()
  }
}
