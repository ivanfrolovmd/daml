// Copyright (c) 2019 The DAML Authors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.http.util

import com.digitalasset.daml.lf.data.ImmArray.ImmArraySeq
import com.digitalasset.ledger.api.v1.event.{ArchivedEvent, CreatedEvent}
import com.digitalasset.ledger.api.v1.transaction.Transaction

object Transactions {
  def allCreatedEvents(transaction: Transaction): ImmArraySeq[CreatedEvent] =
    transaction.events.iterator.flatMap(_.event.created.toList).to[ImmArraySeq]

  def allArchivedEvents(transaction: Transaction): ImmArraySeq[ArchivedEvent] =
    transaction.events.iterator.flatMap(_.event.archived.toList).to[ImmArraySeq]
}
