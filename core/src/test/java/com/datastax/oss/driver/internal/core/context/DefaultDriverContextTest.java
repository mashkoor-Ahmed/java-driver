/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.driver.internal.core.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.internal.core.protocol.Lz4Compressor;
import com.datastax.oss.driver.internal.core.protocol.SnappyCompressor;
import com.datastax.oss.protocol.internal.Compressor;
import com.datastax.oss.protocol.internal.NoopCompressor;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import io.netty.buffer.ByteBuf;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class DefaultDriverContextTest {

  private DefaultDriverContext buildMockedContextWithCompressionOptions(
      Optional<String> compressionOption) {

    DriverExecutionProfile defaultProfile = mock(DriverExecutionProfile.class);
    when(defaultProfile.getString(DefaultDriverOption.PROTOCOL_COMPRESSION, "none"))
        .thenReturn(compressionOption.orElse("none"));
    return MockedDriverContextFactory.defaultDriverContext(Optional.of(defaultProfile));
  }

  private DefaultDriverContext buildMockedContextWithCompressorAndNettyOptions(
      Optional<String> compressionOption, String socksProxyHost, int socksProxyPort) {
    DriverExecutionProfile defaultProfile = mock(DriverExecutionProfile.class);
    when(defaultProfile.getString(DefaultDriverOption.PROTOCOL_COMPRESSION, "none"))
        .thenReturn(compressionOption.orElse("none"));
    when(defaultProfile.isDefined(DefaultDriverOption.SOCKS_PROXY_HOST)).thenReturn(true);
    when(defaultProfile.isDefined(DefaultDriverOption.SOCKS_PROXY_PORT)).thenReturn(true);
    when(defaultProfile.getString(DefaultDriverOption.SOCKS_PROXY_HOST)).thenReturn(socksProxyHost);
    when(defaultProfile.getInt(DefaultDriverOption.SOCKS_PROXY_PORT)).thenReturn(socksProxyPort);
    when(defaultProfile.getString(DefaultDriverOption.NETTY_IO_SHUTDOWN_UNIT))
        .thenReturn(TimeUnit.SECONDS.toString());
    when(defaultProfile.getString(DefaultDriverOption.NETTY_ADMIN_SHUTDOWN_UNIT))
        .thenReturn(TimeUnit.SECONDS.toString());
    when(defaultProfile.getDuration(DefaultDriverOption.NETTY_TIMER_TICK_DURATION))
        .thenReturn(Duration.ofSeconds(1));
    when(defaultProfile.getInt(DefaultDriverOption.NETTY_TIMER_TICKS_PER_WHEEL)).thenReturn(2048);

    return MockedDriverContextFactory.defaultDriverContext(Optional.of(defaultProfile));
  }

  private void doCreateCompressorTest(Optional<String> configVal, Class<?> expectedClz) {

    DefaultDriverContext ctx = buildMockedContextWithCompressionOptions(configVal);
    Compressor<ByteBuf> compressor = ctx.getCompressor();
    assertThat(compressor).isNotNull();
    assertThat(compressor).isInstanceOf(expectedClz);
  }

  @Test
  @DataProvider({"lz4", "lZ4", "Lz4", "LZ4"})
  public void should_create_lz4_compressor(String name) {

    doCreateCompressorTest(Optional.of(name), Lz4Compressor.class);
  }

  @Test
  @DataProvider({"snappy", "SNAPPY", "sNaPpY", "SNapPy"})
  public void should_create_snappy_compressor(String name) {

    doCreateCompressorTest(Optional.of(name), SnappyCompressor.class);
  }

  @Test
  public void should_create_noop_compressor_if_undefined() {

    doCreateCompressorTest(Optional.empty(), NoopCompressor.class);
  }

  @Test
  @DataProvider({"none", "NONE", "NoNe", "nONe"})
  public void should_create_noop_compressor_if_defined_as_none(String name) {

    doCreateCompressorTest(Optional.of(name), NoopCompressor.class);
  }

  @Test
  @DataProvider({"none", "snappy", "lz4"})
  public void should_create_default_netty_options_regardless_of_compressor(String name) {
    DefaultDriverContext ctx =
        buildMockedContextWithCompressorAndNettyOptions(Optional.of(name), "none", 0);
    NettyOptions nettyOptions = ctx.getNettyOptions();
    assertThat(nettyOptions).isInstanceOf(DefaultNettyOptions.class);
    assertThat(nettyOptions).isNotInstanceOf(SocksProxyNettyOptions.class);
  }

  @Test
  @DataProvider({"none", "snappy", "lz4"})
  public void should_create_socks_proxy_netty_options_if_host_and_port_config_provided(
      String name) {
    DefaultDriverContext ctx =
        buildMockedContextWithCompressorAndNettyOptions(
            Optional.of(name), "someproxyurl.com", 1080);
    NettyOptions nettyOptions = ctx.getNettyOptions();
    assertThat(nettyOptions).isInstanceOf(SocksProxyNettyOptions.class);
  }
}
