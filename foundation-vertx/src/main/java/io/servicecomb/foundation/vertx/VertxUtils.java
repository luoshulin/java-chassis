/*
 * Copyright 2017 Huawei Technologies Co., Ltd
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

package io.servicecomb.foundation.vertx;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import javax.xml.ws.Holder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.servicecomb.foundation.vertx.client.AbstractClientVerticle;
import io.servicecomb.foundation.vertx.client.ClientPoolManager;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.FileResolver;
import io.vertx.core.logging.SLF4JLogDelegateFactory;

/**
 * VertxUtils
 *
 *
 */
public final class VertxUtils {
    static {
        // initialize vertx logger, this can be done multiple times
        System.setProperty("vertx.logger-delegate-factory-class-name", SLF4JLogDelegateFactory.class.getName());
        io.vertx.core.logging.LoggerFactory.initialise();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(VertxUtils.class);

    private static final long BLOCKED_THREAD_CHECK_INTERVAL = Long.MAX_VALUE / 2;

    // key为vertx实例名称，以支撑vertx功能分组
    private static Map<String, Vertx> vertxMap = new ConcurrentHashMap<>();

    private VertxUtils() {
    }

    public static <T extends AbstractVerticle> void deployVerticle(Vertx vertx, Class<T> cls, int instanceCount) {
        DeploymentOptions options = new DeploymentOptions().setInstances(instanceCount);

        vertx.deployVerticle(cls.getName(), options);
    }

    public static <CLIENT_POOL, CLIENT_OPTIONS> DeploymentOptions createClientDeployOptions(
            ClientPoolManager<CLIENT_POOL> clientMgr,
            int instanceCount,
            int poolCountPerVerticle, CLIENT_OPTIONS clientOptions) {
        DeploymentOptions options = new DeploymentOptions().setInstances(instanceCount);
        SimpleJsonObject config = new SimpleJsonObject();
        config.put(AbstractClientVerticle.CLIENT_MGR, clientMgr);
        config.put(AbstractClientVerticle.POOL_COUNT, poolCountPerVerticle);
        config.put(AbstractClientVerticle.CLIENT_OPTIONS, clientOptions);
        options.setConfig(config);

        return options;
    }

    public static <VERTICLE extends AbstractVerticle> boolean blockDeploy(Vertx vertx,
            Class<VERTICLE> cls,
            DeploymentOptions options) throws InterruptedException {
        Holder<Boolean> result = new Holder<>();

        CountDownLatch latch = new CountDownLatch(1);
        vertx.deployVerticle(cls.getName(), options, ar -> {
            result.value = ar.succeeded();

            if (ar.failed()) {
                LOGGER.error("deploy vertx failed, cause ", ar.cause());
            }

            latch.countDown();
        });

        latch.await();

        return result.value;
    }

    public static Vertx getOrCreateVertxByName(String name, VertxOptions vertxOptions) {
        Vertx vertx = getVertxByName(name);
        if (vertx == null) {
            synchronized (VertxUtils.class) {
                vertx = getVertxByName(name);
                if (vertx == null) {
                    vertx = init(vertxOptions);
                    vertxMap.put(name, vertx);
                }
            }
        }

        return vertx;
    }

    public static Vertx init(VertxOptions vertxOptions) {
        if (vertxOptions == null) {
            vertxOptions = new VertxOptions();
        }

        boolean isDebug = ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("jdwp") >= 0;
        if (isDebug) {
            vertxOptions.setBlockedThreadCheckInterval(BLOCKED_THREAD_CHECK_INTERVAL);
            LOGGER.info("in debug mode, disable blocked thread check.");
        }

        configureVertxFileCaching();
        return Vertx.vertx(vertxOptions);
    }

    /**
     * 配置vertx的文件缓存功能，默认关闭
     */
    protected static void configureVertxFileCaching() {
        if (System.getProperty(FileResolver.DISABLE_CP_RESOLVING_PROP_NAME) == null) {
            System.setProperty(FileResolver.DISABLE_CP_RESOLVING_PROP_NAME, "true");
        }
    }

    public static Vertx currentVertx() {
        Context context = Vertx.currentContext();
        if (context == null) {
            throw new RuntimeException("get currentVertx error, currentContext is null.");
        }

        return context.owner();
    }

    public static Vertx getVertxByName(String name) {
        return vertxMap.get(name);
    }

    public static <T> void runInContext(Context context, AsyncResultCallback<T> callback, T result, Throwable e) {
        if (context == Vertx.currentContext()) {
            complete(callback, result, e);
        } else {
            context.runOnContext(v -> complete(callback, result, e));
        }
    }

    private static <T> void complete(AsyncResultCallback<T> callback, T result, Throwable e) {
        if (e != null) {
            callback.fail(e.getCause());
            return;
        }

        callback.success(result);
    }
}
