/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.webmonitor.retriever;

import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.runtime.leaderretrieval.LeaderRetrievalListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Retrieves and stores the current leader address.
 */
public class LeaderRetriever implements LeaderRetrievalListener {
	protected final Logger log = LoggerFactory.getLogger(getClass());

	// False if we have to create a new JobManagerGateway future when being notified
	// about a new leader address
	private final AtomicBoolean firstTimeUsage;

	protected volatile CompletableFuture<Tuple2<String, UUID>> leaderFuture;

	public LeaderRetriever() {
		firstTimeUsage = new AtomicBoolean(true);
		leaderFuture = new CompletableFuture<>();
	}

	/**
	 * Returns the current leader information if available. Otherwise it returns an
	 * empty optional.
	 *
	 * @return The current leader information if available. Otherwise it returns an
	 * empty optional.
	 * @throws Exception if the leader future has been completed with an exception
	 */
	public Optional<Tuple2<String, UUID>> getLeaderNow() throws Exception {
		CompletableFuture<Tuple2<String, UUID>> leaderFuture = this.leaderFuture;
		if (leaderFuture != null) {
			CompletableFuture<Tuple2<String, UUID>> currentLeaderFuture = leaderFuture;

			if (currentLeaderFuture.isDone()) {
				return Optional.of(currentLeaderFuture.get());
			} else {
				return Optional.empty();
			}
		} else {
			return Optional.empty();
		}
	}

	/**
	 * Returns the current JobManagerGateway future.
	 */
	public CompletableFuture<Tuple2<String, UUID>> getLeaderFuture() {
		return leaderFuture;
	}

	@Override
	public void notifyLeaderAddress(final String leaderAddress, final UUID leaderSessionID) {
		if (leaderAddress != null && !leaderAddress.equals("")) {
			try {
				final CompletableFuture<Tuple2<String, UUID>> newLeaderFuture;

				if (firstTimeUsage.compareAndSet(true, false)) {
					newLeaderFuture = leaderFuture;
				} else {
					newLeaderFuture = createNewFuture();
					leaderFuture = newLeaderFuture;
				}

				log.info("New leader reachable under {}:{}.", leaderAddress, leaderSessionID);

				newLeaderFuture.complete(Tuple2.of(leaderAddress, leaderSessionID));
			}
			catch (Exception e) {
				handleError(e);
			}
		}
	}

	@Override
	public void handleError(Exception exception) {
		log.error("Received error from LeaderRetrievalService.", exception);

		leaderFuture.completeExceptionally(exception);
	}

	protected CompletableFuture<Tuple2<String, UUID>> createNewFuture() {
		return new CompletableFuture<>();
	}
}