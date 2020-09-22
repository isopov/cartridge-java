package io.tarantool.driver.api.space;

import io.tarantool.driver.TarantoolClientConfig;
import io.tarantool.driver.core.TarantoolConnectionManager;
import io.tarantool.driver.exceptions.TarantoolClientException;
import io.tarantool.driver.api.TarantoolIndexQuery;
import io.tarantool.driver.api.TarantoolIndexQueryFactory;
import io.tarantool.driver.api.TarantoolResult;
import io.tarantool.driver.api.TarantoolSelectOptions;
import io.tarantool.driver.api.tuple.TarantoolTuple;
import io.tarantool.driver.exceptions.TarantoolSpaceNotFoundException;
import io.tarantool.driver.exceptions.TarantoolSpaceOperationException;
import io.tarantool.driver.mappers.TarantoolResultMapperFactory;
import io.tarantool.driver.mappers.ValueConverter;
import io.tarantool.driver.metadata.TarantoolIndexMetadata;
import io.tarantool.driver.metadata.TarantoolMetadataOperations;
import io.tarantool.driver.metadata.TarantoolSpaceMetadata;
import io.tarantool.driver.protocol.TarantoolIteratorType;
import io.tarantool.driver.protocol.TarantoolProtocolException;
import io.tarantool.driver.protocol.TarantoolRequest;
import io.tarantool.driver.protocol.operations.TupleOperations;
import io.tarantool.driver.protocol.requests.TarantoolDeleteRequest;
import io.tarantool.driver.protocol.requests.TarantoolInsertRequest;
import io.tarantool.driver.protocol.requests.TarantoolReplaceRequest;
import io.tarantool.driver.protocol.requests.TarantoolSelectRequest;
import io.tarantool.driver.protocol.requests.TarantoolUpdateRequest;
import io.tarantool.driver.protocol.requests.TarantoolUpsertRequest;
import org.msgpack.value.ArrayValue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Basic Tarantool space operations implementation for standalone server
 *
 * @author Alexey Kuzin
 */
public class TarantoolSpace implements TarantoolSpaceOperations {

    private final int spaceId;
    private final TarantoolClientConfig config;
    private final TarantoolConnectionManager connectionManager;
    private final TarantoolIndexQueryFactory indexQueryFactory;
    private final TarantoolMetadataOperations metadataOperations;
    private final TarantoolResultMapperFactory tarantoolResultMapperFactory;

    /**
     * Basic constructor.
     * @param spaceId internal space ID on Tarantool server
     * @param config client config
     * @param connectionManager Tarantool server connection manager
     * @param metadataOperations metadata operations implementation
     */
    public TarantoolSpace(int spaceId,
                          TarantoolClientConfig config,
                          TarantoolConnectionManager connectionManager,
                          TarantoolMetadataOperations metadataOperations) {
        this.spaceId = spaceId;
        this.config = config;
        this.connectionManager = connectionManager;
        this.indexQueryFactory = new TarantoolIndexQueryFactory(metadataOperations);
        this.metadataOperations = metadataOperations;
        this.tarantoolResultMapperFactory = new TarantoolResultMapperFactory();
    }

    /**
     * Get space ID
     * @return space ID on the Tarantool server
     */
    public int getSpaceId() {
        return spaceId;
    }

    /**
     * Get name of the space
     * @return nullable name wrapped in {@code Optional}
     * @throws TarantoolClientException if failed to retrieve the space information from Tarantool server
     */
    public String getName() throws TarantoolClientException {
        return getMetadata().getSpaceName();
    }

    private TarantoolSpaceMetadata getMetadata() {
        Optional<TarantoolSpaceMetadata> metadata = metadataOperations.getSpaceById(spaceId);
        if (!metadata.isPresent()) {
            throw new TarantoolSpaceNotFoundException(spaceId);
        }
        return metadata.get();
    }

    @Override
    public CompletableFuture<TarantoolResult<TarantoolTuple>> delete(TarantoolIndexQuery indexQuery)
            throws TarantoolClientException {
        ValueConverter<ArrayValue, TarantoolTuple> converter = getDefaultTarantoolTupleValueConverter();
        return delete(indexQuery, converter);
    }

    @Override
    public <T> CompletableFuture<TarantoolResult<T>> delete(TarantoolIndexQuery indexQuery,
                                                            ValueConverter<ArrayValue, T> tupleMapper)
            throws TarantoolClientException {
        try {
            TarantoolDeleteRequest request = new TarantoolDeleteRequest.Builder()
                    .withSpaceId(spaceId)
                    .withIndexId(indexQuery.getIndexId())
                    .withKeyValues(indexQuery.getKeyValues())
                    .build(config.getMessagePackMapper());

            return sendRequest(request, tupleMapper);
        } catch (TarantoolProtocolException e) {
            throw new TarantoolClientException(e);
        }
    }

    @Override
    public CompletableFuture<TarantoolResult<TarantoolTuple>> insert(TarantoolTuple tuple)
            throws TarantoolClientException {
        ValueConverter<ArrayValue, TarantoolTuple> converter = getDefaultTarantoolTupleValueConverter();
        return insert(tuple, converter);
    }

    @Override
    public <T> CompletableFuture<TarantoolResult<T>> insert(TarantoolTuple tuple,
                                                            ValueConverter<ArrayValue, T> tupleMapper)
            throws TarantoolClientException {
        try {
            TarantoolInsertRequest request = new TarantoolInsertRequest.Builder()
                    .withSpaceId(spaceId)
                    .withTuple(tuple)
                    .build(config.getMessagePackMapper());

            return sendRequest(request, tupleMapper);
        } catch (TarantoolProtocolException e) {
            throw new TarantoolClientException(e);
        }
    }

    @Override
    public CompletableFuture<TarantoolResult<TarantoolTuple>> replace(TarantoolTuple tuple)
            throws TarantoolClientException {
        ValueConverter<ArrayValue, TarantoolTuple> converter = getDefaultTarantoolTupleValueConverter();
        return replace(tuple, converter);
    }

    @Override
    public <T> CompletableFuture<TarantoolResult<T>> replace(TarantoolTuple tuple,
                                                             ValueConverter<ArrayValue, T> tupleMapper)
            throws TarantoolClientException {
        try {
            TarantoolReplaceRequest request = new TarantoolReplaceRequest.Builder()
                    .withSpaceId(spaceId)
                    .withTuple(tuple)
                    .build(config.getMessagePackMapper());

            return sendRequest(request, tupleMapper);
        } catch (TarantoolProtocolException e) {
            throw new TarantoolClientException(e);
        }
    }

    @Override
    public CompletableFuture<TarantoolResult<TarantoolTuple>> select(TarantoolSelectOptions options)
            throws TarantoolClientException {
        return select(indexQueryFactory.primary(), options);
    }

    @Override
    public CompletableFuture<TarantoolResult<TarantoolTuple>> select(String indexName,
                                                                     TarantoolSelectOptions options)
            throws TarantoolClientException {
        TarantoolIndexQuery indexQuery = indexQueryFactory.byName(spaceId, indexName);
        return select(indexQuery, options);
    }

    @Override
    public CompletableFuture<TarantoolResult<TarantoolTuple>> select(String indexName,
                                                                     TarantoolIteratorType iteratorType,
                                                                     TarantoolSelectOptions options)
            throws TarantoolClientException {
        TarantoolIndexQuery indexQuery = indexQueryFactory.byName(spaceId, indexName).withIteratorType(iteratorType);
        return select(indexQuery, options);
    }

    @Override
    public CompletableFuture<TarantoolResult<TarantoolTuple>> select(TarantoolIndexQuery indexQuery,
                                                                     TarantoolSelectOptions options)
            throws TarantoolClientException {
        ValueConverter<ArrayValue, TarantoolTuple> converter = getDefaultTarantoolTupleValueConverter();
        return select(indexQuery, options, converter);
    }

    @Override
    public <T> CompletableFuture<TarantoolResult<T>> select(TarantoolIndexQuery indexQuery,
                                                            TarantoolSelectOptions options,
                                                            ValueConverter<ArrayValue, T> tupleMapper)
            throws TarantoolClientException {
        try {
            TarantoolSelectRequest request = new TarantoolSelectRequest.Builder()
                    .withSpaceId(spaceId)
                    .withIndexId(indexQuery.getIndexId())
                    .withIteratorType(indexQuery.getIteratorType())
                    .withKeyValues(indexQuery.getKeyValues())
                    .withLimit(options.getLimit())
                    .withOffset(options.getOffset())
                    .build(config.getMessagePackMapper());

            return sendRequest(request, tupleMapper);
        } catch (TarantoolProtocolException e) {
            throw new TarantoolClientException(e);
        }
    }

    @Override
    public CompletableFuture<TarantoolResult<TarantoolTuple>> update(TarantoolIndexQuery indexQuery,
                                                                     TupleOperations operations) {
        ValueConverter<ArrayValue, TarantoolTuple> converter = getDefaultTarantoolTupleValueConverter();
        return update(indexQuery, operations, converter);
    }

    @Override
    public <T> CompletableFuture<TarantoolResult<T>> update(TarantoolIndexQuery indexQuery,
                                                            TupleOperations operations,
                                                            ValueConverter<ArrayValue, T> tupleMapper)
            throws TarantoolClientException {
        try {
            TarantoolSpaceMetadata metadata = getMetadata();
            Optional<TarantoolIndexMetadata> indexMetadata = metadataOperations
                    .getIndexForId(spaceId, indexQuery.getIndexId());

            if (!indexMetadata.isPresent() || !indexMetadata.get().isUnique()) {
                throw new TarantoolSpaceOperationException("Index must be primary or unique for update operation");
            }

            TarantoolUpdateRequest request = new TarantoolUpdateRequest.Builder(metadata)
                    .withSpaceId(spaceId)
                    .withIndexId(indexQuery.getIndexId())
                    .withKeyValues(indexQuery.getKeyValues())
                    .withTupleOperations(operations)
                    .build(config.getMessagePackMapper());

            return sendRequest(request, tupleMapper);
        } catch (TarantoolProtocolException e) {
            throw new TarantoolClientException(e);
        }
    }

    @Override
    public CompletableFuture<TarantoolResult<TarantoolTuple>> upsert(TarantoolIndexQuery indexQuery,
                                                                     TarantoolTuple tuple,
                                                                     TupleOperations operations) {
        ValueConverter<ArrayValue, TarantoolTuple> converter = getDefaultTarantoolTupleValueConverter();
        return upsert(indexQuery, tuple, operations, converter);
    }

    @Override
    public <T> CompletableFuture<TarantoolResult<T>> upsert(TarantoolIndexQuery indexQuery,
                                                            TarantoolTuple tuple,
                                                            TupleOperations operations,
                                                            ValueConverter<ArrayValue, T> tupleMapper)
            throws TarantoolClientException {
        try {
            TarantoolUpsertRequest request = new TarantoolUpsertRequest.Builder(getMetadata())
                    .withSpaceId(spaceId)
                    .withKeyValues(indexQuery.getKeyValues())
                    .withTuple(tuple)
                    .withTupleOperations(operations)
                    .build(config.getMessagePackMapper());

            return sendRequest(request, tupleMapper);
        } catch (TarantoolProtocolException e) {
            throw new TarantoolClientException(e);
        }
    }

    private <T> CompletableFuture<TarantoolResult<T>> sendRequest(TarantoolRequest request,
                                                                  ValueConverter<ArrayValue, T> tupleMapper) {
        try {
            return connectionManager.getConnection()
                    .sendRequest(request, tarantoolResultMapperFactory.withConverter(tupleMapper));
        } catch (TarantoolProtocolException e) {
            throw new TarantoolClientException(e);
        }
    }

    private ValueConverter<ArrayValue, TarantoolTuple> getDefaultTarantoolTupleValueConverter() {
        Optional<TarantoolSpaceMetadata> meta = metadataOperations.getSpaceById(spaceId);
        return meta.isPresent() ?
                tarantoolResultMapperFactory.getDefaultTupleValueConverter(config.getMessagePackMapper(), meta.get()) :
                tarantoolResultMapperFactory.getDefaultTupleValueConverter(config.getMessagePackMapper());
    }

    @Override
    public String toString() {
        return String.format("TarantoolSpace [%d]", spaceId);
    }
}
