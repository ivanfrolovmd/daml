// Copyright (c) 2019 The DAML Authors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.platform.sandbox.stores.ledger.sql.dao

import java.sql.Connection

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.control.NonFatal

/** A helper to run JDBC queries using a pool of managed connections */
trait JdbcConnectionProvider extends AutoCloseable {

  /** Blocks are running in a single transaction as the commit happens when the connection
    * is returned to the pool.
    * The block must not recursively call [[runSQL]], as this could result in a deadlock
    * waiting for a free connection from the same pool. */
  def runSQL[T](block: Connection => T): T

  /** Returns a connection meant to be used for long running streaming queries. The Connection has to be closed manually! */
  def getStreamingConnection(): Connection
}

object HikariConnection {
  def createDataSource(
      jdbcUrl: String,
      poolName: String,
      minimumIdle: Int,
      maxPoolSize: Int,
      connectionTimeout: FiniteDuration): HikariDataSource = {
    val config = new HikariConfig
    config.setJdbcUrl(jdbcUrl)
    config.setDriverClassName(DbType.jdbcType(jdbcUrl).driver)
    config.addDataSourceProperty("cachePrepStmts", "true")
    config.addDataSourceProperty("prepStmtCacheSize", "128")
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
    config.setAutoCommit(false)
    config.setMaximumPoolSize(maxPoolSize)
    config.setMinimumIdle(minimumIdle)
    config.setConnectionTimeout(connectionTimeout.toMillis)
    config.setPoolName(poolName)

    //note that Hikari uses auto-commit by default.
    //in `runSql` below, the `.close()` will automatically trigger a commit.
    new HikariDataSource(config)
  }
}

class HikariJdbcConnectionProvider(
    jdbcUrl: String,
    noOfShortLivedConnections: Int,
    noOfStreamingConnections: Int)
    extends JdbcConnectionProvider {

  // these connections should never timeout as we have exactly the same number of threads using them as many connections we have
  private val shortLivedDataSource =
    HikariConnection.createDataSource(
      jdbcUrl,
      "Short-Lived-Connections",
      noOfShortLivedConnections,
      noOfShortLivedConnections,
      250.millis)

  // this a dynamic pool as it's used for serving ACS snapshot requests, which we don't expect to get a lot
  private val streamingDataSource =
    HikariConnection.createDataSource(
      jdbcUrl,
      "Streaming-Connections",
      1,
      noOfStreamingConnections,
      60.seconds)

  override def runSQL[T](block: Connection => T): T = {
    val conn = shortLivedDataSource.getConnection()
    conn.setAutoCommit(false)
    try {
      val res = block(conn)
      conn.commit()
      res
    } catch {
      case NonFatal(t) =>
        // Log the error in the caller with access to more logging context (such as the sql statement description)
        conn.rollback()
        throw t
    } finally {
      conn.close()
    }
  }

  override def getStreamingConnection(): Connection =
    streamingDataSource.getConnection()

  override def close(): Unit = {
    shortLivedDataSource.close()
    streamingDataSource.close()
  }
}

object HikariJdbcConnectionProvider {
  def apply(
      jdbcUrl: String,
      noOfShortLivedConnections: Int,
      noOfStreamingConnections: Int): JdbcConnectionProvider =
    new HikariJdbcConnectionProvider(jdbcUrl, noOfShortLivedConnections, noOfStreamingConnections)
}
