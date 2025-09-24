package dev.harborwatch.load;

import java.lang.management.ManagementFactory;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sun.management.OperatingSystemMXBean;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class MetricsScheduler {
	private static final Logger log = LoggerFactory.getLogger(MetricsScheduler.class);
	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	private final JdbcTemplate jdbc;
	private final MeterRegistry registry;
	private final OperatingSystemMXBean osBean;

	// keep previous counters to compute 5s deltas
	private double prevHttpCount = 0.0;
	private double prevHttp5xx = 0.0;

	public MetricsScheduler(JdbcTemplate jdbc, MeterRegistry registry) {
		this.jdbc = jdbc;
		this.registry = registry;
		this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
	}

	@Scheduled(fixedRate = 5000)
	public void writeSnapshot() {
		String appNow = ZonedDateTime.now(IST).toString();

		try {
			// --- HTTP metrics from Micrometer ---
			Timer allReq = registry.find("http.server.requests").tags("outcome", "SUCCESS").timer();
			Timer allReqAny = registry.find("http.server.requests").timer(); // may be null if no traffic yet

			double totalCount = 0.0;
			double totalTimeSec = 0.0;
			if (allReqAny != null) {
				totalCount = allReqAny.count();
				totalTimeSec = allReqAny.totalTime(java.util.concurrent.TimeUnit.SECONDS);
			}

			// 5xx count (errors): sum of timers with status=5xx
			double fiveXxCount = registry.find("http.server.requests").tag("status", "500").timer() != null
					? registry.find("http.server.requests").tag("status", "500").timer().count()
					: 0.0;
			// You can add 501..599 similarly if you want exact sum; or tag on
			// "outcome","SERVER_ERROR" and use that:
			Timer serverErr = registry.find("http.server.requests").tag("outcome", "SERVER_ERROR").timer();
			if (serverErr != null)
				fiveXxCount = serverErr.count();

			// deltas over 5s window
			double deltaCount = Math.max(0, totalCount - prevHttpCount);
			double delta5xx = Math.max(0, fiveXxCount - prevHttp5xx);
			double errorRatePct = (deltaCount > 0 ? (delta5xx / deltaCount) * 100.0 : 0.0);

			prevHttpCount = totalCount;
			prevHttp5xx = fiveXxCount;

			// mean latency over entire lifetime (seconds)
			double meanLatencyMs = (totalCount > 0 ? (totalTimeSec / totalCount) * 1000.0 : 0.0);

			// --- Process/JVM view (app process) ---
			// fraction of one core (0.0..1.0) → convert to percent of a core
			double cpuPctOfCore = (osBean.getProcessCpuLoad() >= 0 ? osBean.getProcessCpuLoad() * 100.0 : 0.0);
			long usedHeap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
			long maxHeap = Math.max(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax(), 1);
			double heapPct = (usedHeap / (double) maxHeap) * 100.0;

			// --- persist ---
			insert("http_requests_5s", deltaCount, Map.of("source", "scheduler"));
			insert("http_5xx_5s", delta5xx, Map.of("source", "scheduler"));
			insert("http_error_rate_pct_5s", errorRatePct, Map.of("source", "scheduler"));
			insert("http_mean_latency_ms", meanLatencyMs, Map.of("source", "scheduler"));

			insert("proc_cpu_pct_of_core", cpuPctOfCore, Map.of("source", "scheduler"));
			insert("jvm_heap_used_pct", heapPct, Map.of("source", "scheduler"));

			log.info("snapshot@{} httpΔ={} 5xxΔ={} err%={} mean_ms={} cpu%={} heap%={}", appNow, (long) deltaCount,
					(long) delta5xx, round1(errorRatePct), round1(meanLatencyMs), round1(cpuPctOfCore),
					round1(heapPct));
		} catch (Exception e) {
			log.error("scheduler snapshot ERROR", e);
		}
	}

	private void insert(String name, double value, Map<String, Object> meta) {
		jdbc.update("INSERT INTO performance_data (timestamp, metric_name, metric_value, metadata) "
				+ "VALUES (now(), ?, ?, ?::jsonb)", name, value, toJson(meta));
	}

	private String toJson(Map<String, Object> m) {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for (var e : m.entrySet()) {
			if (!first)
				sb.append(",");
			first = false;
			sb.append("\"").append(e.getKey()).append("\":");
			sb.append("\"").append(String.valueOf(e.getValue())).append("\"");
		}
		sb.append("}");
		return sb.toString();
	}

	private double round1(double v) {
		return Math.round(v * 10.0) / 10.0;
	}
}
