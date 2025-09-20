package dev.harborwatch.load;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MetricsScheduler {

	private static final Logger log = LoggerFactory.getLogger(MetricsScheduler.class);
	private final JdbcTemplate jdbcTemplate;
	private final Random random = new Random();
	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	public MetricsScheduler(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	// Every 5s, write a 4-row batch into performance_data with explicit DB now()
	@Scheduled(fixedRate = 5000)
	public void writeRandomMetrics() {
		// log the app-side clock for visibility
		String appNow = ZonedDateTime.now(IST).toString();
		log.info("scheduler tick START appTimeIST={}", appNow);

		try {
			double cpu = pct();
			double mem = pct();
			double req = rnd(0, 500);
			double err = pct();

			// NOTE: we set timestamp explicitly to DB now() (timezone IST per DB config)
			insertWithNow("cpu_load", cpu, "{\"source\":\"scheduler\"}");
			insertWithNow("memory_usage", mem, "{\"source\":\"scheduler\"}");
			insertWithNow("request_count", req, "{\"source\":\"scheduler\"}");
			insertWithNow("error_rate", err, "{\"source\":\"scheduler\"}");

			log.info("scheduler tick DONE cpu_load={} memory_usage={} request_count={} error_rate={}", round(cpu),
					round(mem), (int) req, round(err));
		} catch (Exception e) {
			log.error("scheduler tick ERROR", e);
		}
	}

	private void insertWithNow(String name, double value, String metadataJson) {
		jdbcTemplate.update(
				// explicit timestamp via DB now() so we don’t rely on column default
				"INSERT INTO performance_data (timestamp, metric_name, metric_value, metadata) "
						+ "VALUES (now(), ?, ?, ?::jsonb)",
				name, value, metadataJson);
	}

	private double pct() {
		return random.nextDouble() * 100.0;
	}

	private double rnd(int lo, int hi) {
		return lo + random.nextInt(Math.max(1, hi - lo + 1));
	}

	private double round(double v) {
		return Math.round(v * 10.0) / 10.0;
	}
}
