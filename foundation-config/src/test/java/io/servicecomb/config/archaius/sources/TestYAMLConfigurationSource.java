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

package io.servicecomb.config.archaius.sources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

import io.servicecomb.config.archaius.scheduler.NeverStartPollingScheduler;
import org.apache.commons.configuration.SystemConfiguration;
import org.junit.Assert;
import org.junit.Test;

import com.netflix.config.ConcurrentCompositeConfiguration;
import com.netflix.config.ConcurrentMapConfiguration;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicConfiguration;
import com.netflix.config.DynamicDoubleProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.PollResult;

import io.servicecomb.config.ConfigUtil;

/**
 * Created by   on 2017/1/5.
 */
public class TestYAMLConfigurationSource {

    @Test
    public void testPullFromClassPath()
            throws Exception {
        YAMLConfigurationSource configSource = new YAMLConfigurationSource();
        PollResult result = configSource.poll(true, null);
        Map<String, Object> configMap = result.getComplete();
        assertNotNull(configMap);
        assertEquals(19, configMap.size());
        assertNotNull(configMap.get("trace.handler.sampler.percent"));
        assertEquals(0.5, configMap.get("trace.handler.sampler.percent"));
        assertEquals("http://10.120.169.202:9980/", configMap.get("registry.client.serviceUrl.defaultZone"));
        assertNull(configMap.get("eureka.client.serviceUrl.defaultZone"));
    }

    @Test
    public void testPullFroGivenURL()
            throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL test1URL = loader.getResource("test1.yaml");
        URL test2URL = loader.getResource("test2.yaml");
        System.setProperty("cse.configurationSource.additionalUrls", test1URL.toString() + "," + test2URL.toString());
        YAMLConfigurationSource configSource = new YAMLConfigurationSource();
        PollResult result = configSource.poll(true, null);
        Map<String, Object> configMap = result.getComplete();
        assertNotNull(configMap);
        assertEquals(29, configMap.size());
        assertNotNull(configMap.get("trace.handler.sampler.percent"));
        assertEquals(0.5, configMap.get("trace.handler.sampler.percent"));
        System.getProperties().clear();
    }

    @Test
    public void testPullFromGivenFiles()
            throws Exception {
        YAMLConfigurationSource configSource =
                new YAMLConfigurationSource(new File(System.getProperty("user.dir") + "/target/test-classes",
                        "m1.yaml"));
        PollResult result = configSource.poll(true, null);
        Map<String, Object> configMap = result.getComplete();
        assertNotNull(configMap);
        assertNotNull(configMap.get("trace.handler.sampler.percent"));
        assertEquals("okok", configMap.get("zq.test"));
        System.getProperties().clear();
    }

    @Test
    public void testFullOperation() {
        // configuration from system properties
        ConcurrentMapConfiguration configFromSystemProperties =
                new ConcurrentMapConfiguration(new SystemConfiguration());
        // configuration from yaml file
        YAMLConfigurationSource configSource = new YAMLConfigurationSource();
        DynamicConfiguration configFromYamlFile =
                new DynamicConfiguration(configSource, new NeverStartPollingScheduler());
        // create a hierarchy of configuration that makes
        // 1) dynamic configuration source override system properties
        ConcurrentCompositeConfiguration finalConfig = new ConcurrentCompositeConfiguration();
        finalConfig.addConfiguration(configFromYamlFile, "yamlConfig");
        finalConfig.addConfiguration(configFromSystemProperties, "systemEnvConfig");
        ConfigurationManager.install(finalConfig);
        DynamicDoubleProperty myprop =
                DynamicPropertyFactory.getInstance().getDoubleProperty("trace.handler.sampler.percent", 0.1);
        Assert.assertEquals(0.5, myprop.getValue().doubleValue(), 0.5);
        //        DynamicStringListProperty property = new DynamicStringListProperty("trace.handler.tlist", "|", DynamicStringListProperty.DEFAULT_DELIMITER);
        //        List<String> ll = property.get();
        //        for (String s : ll) {
        //            System.out.println(s);
        //        }

        Object o = ConfigUtil.getProperty("zq");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> listO = (List<Map<String, Object>>)o;
        Assert.assertEquals(3, listO.size());
    }
}
