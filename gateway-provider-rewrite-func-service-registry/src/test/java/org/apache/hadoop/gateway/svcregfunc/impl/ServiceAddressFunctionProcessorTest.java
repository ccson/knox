/**
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
package org.apache.hadoop.gateway.svcregfunc.impl;

import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.registry.ServiceRegistry;
import org.apache.hadoop.gateway.svcregfunc.api.ServiceAddressFunctionDescriptor;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.fail;

public class ServiceAddressFunctionProcessorTest {

  ServiceRegistry reg;
  GatewayServices svc;
  UrlRewriteEnvironment env;
  UrlRewriteContext ctx;
  ServiceAddressFunctionDescriptor desc;

  @Before
  public void setUp() {
    reg = EasyMock.createNiceMock( ServiceRegistry.class );
    EasyMock.expect( reg.lookupServiceURL( "test-cluster", "test-service" ) ).andReturn( "test-scheme://test-host:777/test-path" ).anyTimes();

    svc = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( svc.getService( GatewayServices.SERVICE_REGISTRY_SERVICE ) ).andReturn( reg ).anyTimes();

    env = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( env.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( svc ).anyTimes();
    EasyMock.expect( env.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE ) ).andReturn( "test-cluster" ).anyTimes();

    ctx = EasyMock.createNiceMock( UrlRewriteContext.class );

    desc = EasyMock.createNiceMock( ServiceAddressFunctionDescriptor.class );

    EasyMock.replay( reg, svc, env, desc, ctx );
  }

  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( UrlRewriteFunctionProcessor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof ServiceAddressFunctionProcessor ) {
        return;
      }
    }
    fail( "Failed to find " + ServiceAddressFunctionProcessor.class.getName() + " via service loader." );
  }

  @Test
  public void testName() throws Exception {
    ServiceAddressFunctionProcessor func = new ServiceAddressFunctionProcessor();
    assertThat( func.name(), is( "serviceAddr" ) );
  }

  @Test
  public void testInitialize() throws Exception {
    ServiceAddressFunctionProcessor func = new ServiceAddressFunctionProcessor();
    try {
      func.initialize( null, desc );
      fail( "Should have thrown an IllegalArgumentException" );
    } catch( IllegalArgumentException e ) {
      assertThat( e.getMessage(), containsString( "environment" ) );
    }

    func = new ServiceAddressFunctionProcessor();
    try {
      func.initialize( env, null );
    } catch( Exception e ) {
      e.printStackTrace();
      fail( "Should not have thrown an exception" );
    }

    func.initialize( env, desc );

    assertThat( func.cluster(), is( "test-cluster" ) );
    assertThat( func.registry(), sameInstance( reg ) );
  }

  @Test
  public void testDestroy() throws Exception {
    ServiceAddressFunctionProcessor func = new ServiceAddressFunctionProcessor();
    func.initialize( env, desc );
    func.destroy();

    assertThat( func.cluster(), nullValue() );
    assertThat( func.registry(), nullValue() );
  }

  @Test
  public void testResolve() throws Exception {
    ServiceAddressFunctionProcessor func = new ServiceAddressFunctionProcessor();
    func.initialize( env, desc );

    assertThat( func.resolve( ctx, Arrays.asList( "test-service" ) ), contains( "test-host:777" ) );
    assertThat( func.resolve( ctx, Arrays.asList( "invalid-test-service" ) ), contains( "invalid-test-service" ) );
    assertThat( func.resolve( ctx, null ), nullValue() );

    func.destroy();
  }

}