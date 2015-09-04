package org.stagemonitor.jvm;


import static org.stagemonitor.core.metrics.metrics2.MetricName.name;

import java.util.Collections;
import java.util.List;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.core.CorePlugin;
import org.stagemonitor.core.StagemonitorPlugin;
import org.stagemonitor.core.configuration.Configuration;
import org.stagemonitor.core.elasticsearch.ElasticsearchClient;
import org.stagemonitor.core.metrics.metrics2.Metric2Registry;
import org.stagemonitor.core.metrics.metrics2.Metric2Set;
import org.stagemonitor.core.metrics.metrics2.MetricName;
import org.stagemonitor.core.metrics.metrics2.MetricNameConverter;

public class JvmPlugin extends StagemonitorPlugin {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void initializePlugin(Metric2Registry registry, Configuration configuration) {
		registry.registerAll(Metric2Set.Converter.convert(new GarbageCollectorMetricSet(), new MetricNameConverter() {
			@Override
			public MetricName convert(String name) {
				final String[] split = name.split("\\.");
				return name("jvm_gc_" + split[1]).tag("collector", split[0]).build();
			}
		}));
		registry.registerAll(Metric2Set.Converter.convert(new MemoryUsageGaugeSet(), new MetricNameConverter() {
			@Override
			public MetricName convert(String name) {
				final String[] split = name.split("\\.");
				if (split.length == 3) {
					return name("jvm_memory_" + split[0]).tag("memory_pool", split[1]).type(split[2]).build();
				}
				return name("jvm_memory_" + split[0].replace('-', '_')).type(split[1]).build();
			}
		}));

		final CpuUtilisationWatch cpuWatch;
		try {
			cpuWatch = new CpuUtilisationWatch();
			cpuWatch.start();
			registry.register(name("jvm_process_cpu_usage").build(), new Gauge<Float>() {
				@Override
				public Float getValue() {
					try {
						return cpuWatch.getCpuUsagePercent();
					} finally {
						cpuWatch.start();
					}
				}
			});
		} catch (Exception e) {
			logger.warn("Could not register cpu usage. ({})", e.getMessage());
		}

		ElasticsearchClient elasticsearchClient = configuration.getConfig(CorePlugin.class).getElasticsearchClient();
		elasticsearchClient.sendGrafanaDashboardAsync("JVM Memory.json");
		elasticsearchClient.sendGrafanaDashboardAsync("JVM Overview.json");
	}

	@Override
	public List<String> getPathsOfWidgetMetricTabPlugins() {
		return Collections.singletonList("/stagemonitor/static/tabs/metrics/jvm-metrics");
	}

}
