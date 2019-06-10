/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.common.operator.resource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.EditReplacePatchDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(VertxUnitRunner.class)
public abstract class AbstractResourceOperatorTest<C extends KubernetesClient, T extends HasMetadata,
        L extends KubernetesResourceList, D, R extends Resource<T, D>> {

    public static final String RESOURCE_NAME = "my-resource";
    public static final String NAMESPACE = "test";
    protected static Vertx vertx;

    @BeforeClass
    public static void before() {
        vertx = Vertx.vertx();
    }

    @AfterClass
    public static void after() {
        vertx.close();
    }

    /**
     * The type of kubernetes client to be mocked
     */
    protected abstract Class<C> clientType();

    /**
     * The type of the resource being tested
     */
    protected abstract Class<? extends Resource> resourceType();

    /**
     * Get a (new) test resource
     */
    protected abstract T resource();

    /**
     * Configure the given {@code mockClient} to return the given {@code op}
     * that's appropriate for the kind of resource being tests.
     */
    protected abstract void mocker(C mockClient, MixedOperation op);

    /** Create the subclass of ResourceOperation to be tested */
    protected abstract AbstractResourceOperator<C, T, L, D, R> createResourceOperations(Vertx vertx, C mockClient);

    /** Create the subclass of ResourceOperation to be tested with mocked readiness checks*/
    protected AbstractResourceOperator<C, T, L, D, R> createResourceOperationsWithMockedReadiness(Vertx vertx, C mockClient)    {
        return createResourceOperations(vertx, mockClient);
    }

    @Test
    public void createWhenExistsIsAPatch(TestContext context) {
        createWhenExistsIsAPatch(context, true);
    }

    public void createWhenExistsIsAPatch(TestContext context, boolean cascade) {
        T resource = resource();
        Resource mockResource = mock(resourceType());
        when(mockResource.get()).thenReturn(resource);
        when(mockResource.cascading(cascade)).thenReturn(mockResource);

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(resource.getMetadata().getName()))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(resource.getMetadata().getNamespace()))).thenReturn(mockNameable);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);

        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperations(vertx, mockClient);

        Async async = context.async();
        Future<ReconcileResult<T>> fut = op.createOrUpdate(resource());
        fut.setHandler(ar -> {
            if (!ar.succeeded()) {
                ar.cause().printStackTrace();
            }
            assertTrue(ar.succeeded());
            verify(mockResource).get();
            verify(mockResource).patch(any());
            verify(mockResource, never()).create(any());
            verify(mockResource, never()).createNew();
            verify(mockResource, never()).createOrReplace(any());
            verify(mockCms, never()).createOrReplace(any());
            async.complete();
        });
    }

    @Test
    public void existenceCheckThrows(TestContext context) {
        T resource = resource();
        RuntimeException ex = new RuntimeException();

        Resource mockResource = mock(resourceType());
        when(mockResource.get()).thenThrow(ex);

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(resource.getMetadata().getName()))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(resource.getMetadata().getNamespace()))).thenReturn(mockNameable);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);

        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperations(vertx, mockClient);

        Async async = context.async();
        op.createOrUpdate(resource).setHandler(ar -> {
            assertTrue(ar.failed());
            assertEquals(ex, ar.cause());
            async.complete();
        });
    }

    @Test
    public void successfulCreation(TestContext context) {
        T resource = resource();
        Resource mockResource = mock(resourceType());
        when(mockResource.get()).thenReturn(null);

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(resource.getMetadata().getName()))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(resource.getMetadata().getNamespace()))).thenReturn(mockNameable);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);

        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperationsWithMockedReadiness(vertx, mockClient);

        Async async = context.async();
        op.createOrUpdate(resource).setHandler(ar -> {
            if (ar.failed()) ar.cause().printStackTrace();
            assertTrue(ar.succeeded());
            verify(mockResource).get();
            verify(mockResource).create(eq(resource));
            async.complete();
        });
    }

    @Test
    public void creationThrows(TestContext context) {
        T resource = resource();
        RuntimeException ex = new RuntimeException("Testing this exception is handled correctly");

        Resource mockResource = mock(resourceType());
        when(mockResource.get()).thenReturn(null);

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(resource.getMetadata().getName()))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(resource.getMetadata().getNamespace()))).thenReturn(mockNameable);
        when(mockResource.create(any())).thenThrow(ex);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);

        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperations(vertx, mockClient);

        Async async = context.async();
        op.createOrUpdate(resource).setHandler(ar -> {
            assertTrue(ar.failed());
            assertEquals(ex, ar.cause());
            async.complete();
        });
    }

    @Test
    public void deleteWhenResourceDoesNotExistIsANop(TestContext context) {
        T resource = resource();
        Resource mockResource = mock(resourceType());

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(RESOURCE_NAME))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(NAMESPACE))).thenReturn(mockNameable);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);

        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperations(vertx, mockClient);

        Async async = context.async();
        op.reconcile(resource.getMetadata().getNamespace(), resource.getMetadata().getName(), null).setHandler(ar -> {
            assertTrue(ar.succeeded());
            verify(mockResource).get();
            verify(mockResource, never()).delete();
            async.complete();
        });
    }

    @Test
    public void deleteWhenResourceExistsStillDeletes(TestContext context) {
        EditReplacePatchDeletable mockERPD = mock(EditReplacePatchDeletable.class);

        T resource = resource();
        Resource mockResource = mock(resourceType());
        when(mockResource.get()).thenReturn(resource);
        when(mockResource.cascading(eq(true))).thenReturn(mockERPD);

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(RESOURCE_NAME))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(NAMESPACE))).thenReturn(mockNameable);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);

        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperations(vertx, mockClient);

        Async async = context.async();
        op.reconcile(resource.getMetadata().getNamespace(), resource.getMetadata().getName(), null).setHandler(ar -> {
            assertTrue(ar.succeeded());
            verify(mockERPD).delete();
            async.complete();
        });
    }

    @Test
    public void successfulDeletion(TestContext context) {
        EditReplacePatchDeletable mockERPD = mock(EditReplacePatchDeletable.class);

        T resource = resource();
        Resource mockResource = mock(resourceType());
        when(mockResource.get()).thenReturn(resource);
        when(mockResource.cascading(eq(true))).thenReturn(mockERPD);

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(RESOURCE_NAME))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(NAMESPACE))).thenReturn(mockNameable);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);

        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperations(vertx, mockClient);

        Async async = context.async();
        op.reconcile(resource.getMetadata().getNamespace(), resource.getMetadata().getName(), null).setHandler(ar -> {
            if (ar.failed()) ar.cause().printStackTrace();
            assertTrue(ar.succeeded());
            verify(mockERPD).delete();
            async.complete();
        });
    }

    @Test
    public void deletionThrows(TestContext context) {
        RuntimeException ex = new RuntimeException("Testing this exception is handled correctly");
        EditReplacePatchDeletable mockERPD = mock(EditReplacePatchDeletable.class);
        when(mockERPD.delete()).thenThrow(ex);

        T resource = resource();
        Resource mockResource = mock(resourceType());
        when(mockResource.get()).thenReturn(resource);
        when(mockResource.cascading(eq(true))).thenReturn(mockERPD);

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(RESOURCE_NAME))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(NAMESPACE))).thenReturn(mockNameable);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);

        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperations(vertx, mockClient);

        Async async = context.async();
        op.reconcile(resource.getMetadata().getNamespace(), resource.getMetadata().getName(), null).setHandler(ar -> {
            assertTrue(ar.failed());
            assertEquals(ex, ar.cause());
            async.complete();
        });
    }

    @Test
    public void watchFor_predImmediatelySatisfied(TestContext testContext) throws InterruptedException {
        T resource = resource();
        Resource mockResource = mock(resourceType());
        when(mockResource.get()).thenReturn(resource);
        AtomicBoolean watchClosed = new AtomicBoolean(false);
        when(mockResource.watch(any())).thenReturn(new Watch() {
            @Override
            public void close() {
                watchClosed.set(true);
            }
        });

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(RESOURCE_NAME))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(NAMESPACE))).thenReturn(mockNameable);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);

        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperations(vertx, mockClient);
        AtomicInteger predCallCount = new AtomicInteger();
        Async async = testContext.async();
        op.watchFor(this.toString(), resource.getMetadata().getNamespace(), resource.getMetadata().getName(), 1_000,
            r -> {
                predCallCount.incrementAndGet();
                return true;
            }).setHandler(ar -> {
                testContext.assertTrue(ar.succeeded());
                testContext.assertEquals(1, predCallCount.get());
                async.complete();
            });
        async.await();
        Thread.sleep(1_000);
        testContext.assertTrue(watchClosed.get());
    }

    @Test
    public void watchFor_predImmediatelyThrows(TestContext testContext) throws InterruptedException {
        T resource = resource();
        Resource mockResource = mock(resourceType());
        when(mockResource.get()).thenReturn(resource);
        AtomicBoolean watchClosed = new AtomicBoolean(false);
        when(mockResource.watch(any())).thenReturn(new Watch() {
            @Override
            public void close() {
                watchClosed.set(true);
            }
        });

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(RESOURCE_NAME))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(NAMESPACE))).thenReturn(mockNameable);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);
        Async async = testContext.async();
        AtomicInteger predCallCount = new AtomicInteger(0);
        RuntimeException runtimeException = new RuntimeException();
        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperations(vertx, mockClient);
        op.watchFor(this.toString(), resource.getMetadata().getNamespace(), resource.getMetadata().getName(), 1_000,
            r -> {
                if (predCallCount.getAndIncrement() == 0) {
                    throw runtimeException;
                }
                return true;
            }).setHandler(ar -> {
                //testContext.assertTrue(ar.succeeded());
                //testContext.assertEquals(2, predCallCount.get());
                async.complete();
            });

        async.await();
        assertWatchClosed(testContext, watchClosed);
    }

    @Test
    public void watchFor_predEventuallySatisfied(TestContext testContext) throws InterruptedException {
        T resource = resource();
        Resource mockResource = mock(resourceType());
        when(mockResource.get()).thenReturn(resource);
        AtomicBoolean watchClosed = new AtomicBoolean(false);
        AtomicReference<Watcher<T>> watcherRef = new AtomicReference<>();
        when(mockResource.watch(any())).thenAnswer(invocation -> {
            Watcher<T> watcher = invocation.getArgument(0);
            watcherRef.set(watcher);
            return new Watch() {
                @Override
                public void close() {
                    watchClosed.set(true);
                }
            };
        });

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(RESOURCE_NAME))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(NAMESPACE))).thenReturn(mockNameable);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);

        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperations(vertx, mockClient);
        AtomicInteger predCallCount = new AtomicInteger(0);
        Async async = testContext.async();
        op.watchFor(this.toString(), resource.getMetadata().getNamespace(), resource.getMetadata().getName(), 10_000,
            r -> {
                return predCallCount.incrementAndGet() >= 2;
            }).setHandler(ar -> {
                if (!ar.succeeded()) ar.cause().printStackTrace();
                testContext.assertTrue(ar.succeeded());
                testContext.assertEquals(2, predCallCount.get());
                async.complete();
            });
        while (watcherRef.get() == null) {
            // spin
        }
        // We have to sleep otherwise the event is delivered too quickly and is handled by the "initial get" case.
        Thread.sleep(500);
        watcherRef.get().eventReceived(Watcher.Action.MODIFIED, null);
        async.await();
        assertWatchClosed(testContext, watchClosed);
    }

    @Test
    public void watchFor_predEventuallyThrows(TestContext testContext) throws InterruptedException {
        T resource = resource();
        Resource mockResource = mock(resourceType());
        when(mockResource.get()).thenReturn(resource);
        AtomicBoolean watchClosed = new AtomicBoolean(false);
        AtomicReference<Watcher<T>> watcherRef = new AtomicReference<>();
        when(mockResource.watch(any())).thenAnswer(invocation -> {
            Watcher<T> watcher = invocation.getArgument(0);
            watcherRef.set(watcher);
            return new Watch() {
                @Override
                public void close() {
                    watchClosed.set(true);
                }
            };
        });

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(RESOURCE_NAME))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(NAMESPACE))).thenReturn(mockNameable);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);

        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperations(vertx, mockClient);
        AtomicInteger predCallCount = new AtomicInteger();
        Async async = testContext.async();
        RuntimeException runtimeException = new RuntimeException();
        op.watchFor(this.toString(), resource.getMetadata().getNamespace(), resource.getMetadata().getName(), 1_000,
            r -> {
                int i = predCallCount.getAndIncrement();
                if (i == 0) {
                    return false;
                } else if (i == 1) {
                    throw runtimeException;
                } else {
                    return true;
                }
            }).setHandler(ar -> {
                testContext.assertTrue(ar.failed());
                testContext.assertEquals(runtimeException, ar.cause());
                testContext.assertEquals(2, predCallCount.get());
                async.complete();
            });
        while (watcherRef.get() == null) {
            // spin
        }
        // We have to sleep otherwise the event is delivered too quickly and is handled by the "initial get" case.
        Thread.sleep(500);
        watcherRef.get().eventReceived(Watcher.Action.MODIFIED, null);
        watcherRef.get().eventReceived(Watcher.Action.MODIFIED, null);
        async.await();
        assertWatchClosed(testContext, watchClosed);
    }

    @Test
    public void watchFor_timeout(TestContext testContext) throws InterruptedException {
        T resource = resource();
        Resource mockResource = mock(resourceType());
        when(mockResource.get()).thenReturn(resource);
        AtomicBoolean watchClosed = new AtomicBoolean(false);
        AtomicReference<Watcher<T>> watcherRef = new AtomicReference<>();
        when(mockResource.watch(any())).thenAnswer(invocation -> {
            Watcher<T> watcher = invocation.getArgument(0);
            watcherRef.set(watcher);
            return new Watch() {
                @Override
                public void close() {
                    watchClosed.set(true);
                }
            };
        });

        NonNamespaceOperation mockNameable = mock(NonNamespaceOperation.class);
        when(mockNameable.withName(matches(RESOURCE_NAME))).thenReturn(mockResource);

        MixedOperation mockCms = mock(MixedOperation.class);
        when(mockCms.inNamespace(matches(NAMESPACE))).thenReturn(mockNameable);

        C mockClient = mock(clientType());
        mocker(mockClient, mockCms);

        AbstractResourceOperator<C, T, L, D, R> op = createResourceOperations(vertx, mockClient);
        AtomicInteger predCallCount = new AtomicInteger();
        Async async = testContext.async();
        op.watchFor(this.toString(), resource.getMetadata().getNamespace(), resource.getMetadata().getName(), 1_000,
            r -> {
                return false;
            }).setHandler(ar -> {
                testContext.assertTrue(ar.failed());
                testContext.assertTrue(ar.cause() instanceof TimeoutException);
                async.complete();
            });
        async.await();
        assertWatchClosed(testContext, watchClosed);
    }

    void assertWatchClosed(TestContext testContext, AtomicBoolean watchClosed) throws InterruptedException {
        int i = 100;
        while (!watchClosed.get()) {
            if (i-- == 0) {
                testContext.fail("watch not closed");
            }
            Thread.sleep(100);
        }
    }

}

