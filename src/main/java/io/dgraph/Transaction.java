/*
 * Copyright (C) 2018 Dgraph Labs, Inc. and Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.dgraph;

import io.dgraph.DgraphProto.Assigned;
import io.dgraph.DgraphProto.Mutation;
import io.dgraph.DgraphProto.Response;
import java.util.Collections;
import java.util.Map;

/**
 * This is synchronous implementation of Dgraph transaction. All operations are delegated to
 * asynchronous implementation.
 *
 * @author Edgar Rodriguez-Diaz
 * @author Deepak Jois
 * @author Michail Klimenkov
 */
public class Transaction implements AutoCloseable {
  private final AsyncTransaction asyncTransaction;

  public Transaction(AsyncTransaction asyncTransaction) {
    this.asyncTransaction = asyncTransaction;
  }

  /**
   * Sends a query to one of the connected dgraph instances. If no mutations need to be made in the
   * same transaction, it's convenient to chain the method: <code>
   * client.NewTransaction().queryWithVars(...)</code>.
   *
   * @param query Query in GraphQL+-
   * @param vars variables referred to in the queryWithVars.
   * @return a Response protocol buffer object.
   */
  public Response queryWithVars(final String query, final Map<String, String> vars) {
    return ExceptionUtil.withExceptionUnwrapped(
        () -> {
          return asyncTransaction.queryWithVars(query, vars).join();
        });
  }

  /**
   * Calls {@code Transcation#queryWithVars} with an empty vars map.
   *
   * @param query Query in GraphQL+-
   * @return a Response protocol buffer object
   */
  public Response query(final String query) {
    return queryWithVars(query, Collections.emptyMap());
  }

  /**
   * Allows data stored on dgraph instances to be modified. The fields in Mutation come in pairs,
   * set and delete. Mutations can either be encoded as JSON or as RDFs.
   *
   * <p>If the commitNow property on the Mutation object is set,
   *
   * @param mutation a Mutation protocol buffer object representing the mutation.
   * @return an Assigned protocol buffer object. his call will result in the transaction being
   *     committed. In this case, an explicit call to AsyncTransaction#commit doesn't need to
   *     subsequently be made.
   */
  public Assigned mutate(Mutation mutation) {
    return ExceptionUtil.withExceptionUnwrapped(
        () -> {
          return asyncTransaction.mutate(mutation).join();
        });
  }

  /**
   * Commits any mutations that have been made in the transaction. Once Commit has been called, the
   * lifespan of the transaction is complete.
   *
   * <p>Errors could be thrown for various reasons. Notably, a StatusRuntimeException could be
   * thrown if transactions that modify the same data are being run concurrently. It's up to the
   * user to decide if they wish to retry. In this case, the user should create a new transaction.
   */
  public void commit() {
    ExceptionUtil.withExceptionUnwrapped(
        () -> {
          asyncTransaction.commit().join();
        });
  }

  /**
   * Cleans up the resources associated with an uncommitted transaction that contains mutations. It
   * is a no-op on transactions that have already been committed or don't contain mutations.
   * Therefore it is safe (and recommended) to call it in a finally block.
   *
   * <p>In some cases, the transaction can't be discarded, e.g. the grpc connection is unavailable.
   * In these cases, the server will eventually do the transaction clean up.
   */
  public void discard() {
    ExceptionUtil.withExceptionUnwrapped(
        () -> {
          asyncTransaction.discard().join();
        });
  }

  /**
   * Sets the best effort flag for this transaction. The Best effort flag can only be set for
   * read-only transactions, and setting the best effort flag will enable a read-only transaction to
   * see mutations made by other transactions even if those mutations have not been committed.
   */
  public void setBestEffort(boolean bestEffort) {
    asyncTransaction.setBestEffort(bestEffort);
  }

  @Override
  public void close() {
    ExceptionUtil.withExceptionUnwrapped(this::discard);
  }
}
