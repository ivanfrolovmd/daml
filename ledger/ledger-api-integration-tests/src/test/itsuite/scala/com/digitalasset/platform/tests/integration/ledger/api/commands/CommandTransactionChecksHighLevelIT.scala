// Copyright (c) 2019 The DAML Authors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.platform.tests.integration.ledger.api.commands

import com.digitalasset.api.util.TimeProvider
import com.digitalasset.grpc.{GrpcException, GrpcStatus}
import com.digitalasset.ledger.api.v1.command_service.{
  SubmitAndWaitForTransactionIdResponse,
  SubmitAndWaitRequest
}
import com.digitalasset.ledger.api.v1.command_submission_service.SubmitRequest
import com.digitalasset.ledger.api.v1.completion.Completion
import com.digitalasset.ledger.client.services.commands.CommandUpdater
import com.digitalasset.platform.apitesting.{CommandTransactionChecks, LedgerContext}
import com.digitalasset.platform.services.time.TimeProviderType.{
  Static,
  StaticAllowBackwards,
  WallClock
}
import com.google.rpc.code.Code
import com.google.rpc.status.Status

import scala.concurrent.Future

class CommandTransactionChecksHighLevelIT extends CommandTransactionChecks {
  private[this] def responseToCompletion(
      commandId: String,
      txF: Future[SubmitAndWaitForTransactionIdResponse]): Future[Completion] =
    txF
      .map(tx => Completion(commandId, Some(Status(Code.OK.value, "")), tx.transactionId))
      .recover {
        case GrpcException(status, _) =>
          Completion(commandId, Some(GrpcStatus.toProto(status)))
      }

  def commandUpdater(ctx: LedgerContext) = {
    val timeProvider = config.timeProviderType match {
      case Static | StaticAllowBackwards => None
      case WallClock => Some(TimeProvider.UTC)
    }
    new CommandUpdater(
      timeProvider,
      java.time.Duration.ofMillis(config.commandConfiguration.commandTtl.toMillis),
      true)
  }

  override protected def submitCommand(
      ctx: LedgerContext,
      submitRequest: SubmitRequest): Future[Completion] = {
    responseToCompletion(
      submitRequest.commands.value.commandId,
      ctx.commandService.submitAndWaitForTransactionId(
        SubmitAndWaitRequest(
          submitRequest.commands.map(commandUpdater(ctx).applyOverrides),
          submitRequest.traceContext))
    )
  }
}
