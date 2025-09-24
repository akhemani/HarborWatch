/*
 * DebugController
 * Purpose:
 *   - Read-only endpoints to "see" what's in the DB without SQL.
 *   - Helps connect logs ↔ data while you're learning.
 * Notes:
 *   - Safe for dev; in prod you'd lock this behind auth and/or remove it.
 */
package dev.harborwatch.debug;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DebugController {

	private final JdbcTemplate jdbc;
	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	public DebugController(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping("/api/debug/summary")
	public Map<String, Object> summary() {
		Integer perfCount = jdbc.queryForObject("SELECT COUNT(*) FROM performance_data", Integer.class);
		Integer compCount = jdbc.queryForObject("SELECT COUNT(*) FROM computation_results", Integer.class);
		return Map.of("performance_data_count", perfCount, "computation_results_count", compCount);
	}

	@GetMapping("/api/debug/performance/recent")
	public List<Map<String, Object>> recentPerformance() {
		return jdbc.queryForList(
				"SELECT id, timestamp, metric_name, metric_value " + "FROM performance_data ORDER BY id DESC LIMIT 10");
	}

	@GetMapping("/api/debug/computations/recent")
	public List<Map<String, Object>> recentComputations() {
		return jdbc.queryForList("SELECT id, timestamp, computation_type, input_size, duration_ms "
				+ "FROM computation_results ORDER BY id DESC LIMIT 10");
	}

	@GetMapping("/api/debug/now")
	public Map<String, Object> now() {
		// DB now() in IST (DB timezone is Asia/Kolkata per migration)
		Map<String, Object> dbNowRow = jdbc.queryForMap("SELECT now() AS db_now");
		// Most recent performance_data/computation_results rows (if any)
		Map<String, Object> lastPerf = jdbc
				.queryForMap("SELECT id, timestamp, metric_name FROM performance_data ORDER BY id DESC LIMIT 1");
		Map<String, Object> lastComp = jdbc.queryForMap(
				"SELECT id, timestamp, computation_type FROM computation_results ORDER BY id DESC LIMIT 1");
		return Map.of("app_now_ist", ZonedDateTime.now(IST).toString(), "db_now_ist", dbNowRow.get("db_now").toString(),
				"last_performance_data", lastPerf, "last_computation_result", lastComp);
	}
}
