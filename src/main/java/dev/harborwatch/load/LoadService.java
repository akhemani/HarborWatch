/*
 * LoadService
 * What this class does:
 *   - Implements CPU, Memory, DB, and Combined stress routines.
 *   - Each method logs a START line (with inputs) and a DONE line (with outputs).
 * Why logs matter here:
 *   - You can "trace" exactly what was run, with what parameters, and how long it took.
 *   - If we clamp inputs (guards), we WARN so you know caps were applied.
 *
 * Important:
 *   - We DO NOT store logs in the database. Logs go to stdout.
 *   - We DO store "results/metrics" in DB tables so you can query them with SQL.
 */
package dev.harborwatch.load;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoadService {

	private static final Logger log = LoggerFactory.getLogger(LoadService.class);

	private final JdbcTemplate jdbcTemplate;
	private final Random random = new Random();

	// Safety caps (so you don't nuke your laptop during tests)
	private static final int MAX_ITERATIONS = 50_000_000;
	private static final int MAX_SIZE_MB = 256;
	private static final int MAX_DB_OPS = 10_000;

	public LoadService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/* ---------------- CPU-INTENSIVE ---------------- */

	public Map<String, Object> cpuIntensive(int iterations) {
		int requested = iterations;
		if (iterations > MAX_ITERATIONS) {
			log.warn("cpuIntensive: requested iterations={} exceeds cap {}; clamping", iterations, MAX_ITERATIONS);
			iterations = MAX_ITERATIONS;
		}
		if (iterations < 1)
			iterations = 1;

		log.info("cpuIntensive START iterationsRequested={} iterationsActual={}", requested, iterations);
		Instant start = Instant.now();

		double acc = 0.0;
		for (int i = 1; i <= iterations; i++) {
			acc += Math.sin(i) * Math.cos(i >> 3) + Math.sqrt(i);
			if ((i & 8191) == 0)
				acc = acc / 1.000001d + (i % 7);
		}

		long durationMs = Duration.between(start, Instant.now()).toMillis();

		// Persist a "result summary" to the DB (NOT logs)
		jdbcTemplate.update(
				"INSERT INTO computation_results (computation_type, input_size, result, duration_ms) VALUES (?,?,?,?)",
				"cpu_intensive", iterations, "acc=" + String.format(Locale.ROOT, "%.6f", acc), durationMs);

		log.info("cpuIntensive DONE iterationsActual={} durationMs={} accSample={}", iterations, durationMs,
				String.format(Locale.ROOT, "%.6f", acc));

		return Map.of("type", "cpu_intensive", "iterations", iterations, "durationMs", durationMs, "accSample",
				String.format(Locale.ROOT, "%.6f", acc));
	}

	/* ---------------- MEMORY-INTENSIVE ---------------- */

	public Map<String, Object> memoryIntensive(int sizeMb) {
		int requested = sizeMb;
		if (sizeMb > MAX_SIZE_MB) {
			log.warn("memoryIntensive: requested sizeMb={} exceeds cap {}; clamping", sizeMb, MAX_SIZE_MB);
			sizeMb = MAX_SIZE_MB;
		}
		if (sizeMb < 1)
			sizeMb = 1;

		log.info("memoryIntensive START sizeMbRequested={} sizeMbActual={}", requested, sizeMb);
		Instant start = Instant.now();

		List<byte[]> blocks = new ArrayList<>(sizeMb);
		for (int i = 0; i < sizeMb; i++)
			blocks.add(new byte[1024 * 1024]);

		long checksum = 0;
		for (byte[] b : blocks) {
			for (int i = 0; i < b.length; i += 4096) {
				b[i] = (byte) (i & 0xFF);
				checksum += b[i];
			}
		}
		long durationMs = Duration.between(start, Instant.now()).toMillis();

		// drop references so GC can reclaim
		blocks.clear();

		log.info("memoryIntensive DONE sizeMbActual={} durationMs={} checksum={}", sizeMb, durationMs, checksum);
		return Map.of("type", "memory_intensive", "sizeMb", sizeMb, "durationMs", durationMs, "checksum", checksum);
	}

	/* ---------------- DATABASE-INTENSIVE ---------------- */

	public Map<String, Object> databaseIntensive(int ops) {
		int requested = ops;
		if (ops > MAX_DB_OPS) {
			log.warn("databaseIntensive: requested ops={} exceeds cap {}; clamping", ops, MAX_DB_OPS);
			ops = MAX_DB_OPS;
		}
		if (ops < 1)
			ops = 1;

		log.info("databaseIntensive START opsRequested={} opsActual={}", requested, ops);
		Instant start = Instant.now();
		int reads = 0;

		for (int i = 0; i < ops; i++) {
			// write a metric row (this is DATA, not LOGS)
			jdbcTemplate.update(
					"INSERT INTO performance_data (metric_name, metric_value, metadata) VALUES (?, ?, '{}'::jsonb)",
					"test_metric_" + i, random.nextDouble() * 100.0);

			// sample read
			if ((i % 25) == 0) {
				jdbcTemplate.queryForList(
						"SELECT id FROM performance_data WHERE metric_name LIKE ? ORDER BY id DESC LIMIT 10",
						"test_metric%");
				reads++;
			}
		}

		long durationMs = Duration.between(start, Instant.now()).toMillis();

		// summarize the batch
		jdbcTemplate.update(
				"INSERT INTO computation_results (computation_type, input_size, result, duration_ms) VALUES (?,?,?,?)",
				"database_intensive", ops, "reads=" + reads, durationMs);

		log.info("databaseIntensive DONE opsActual={} reads={} durationMs={}", ops, reads, durationMs);
		return Map.of("type", "database_intensive", "ops", ops, "reads", reads, "durationMs", durationMs);
	}

	/* ---------------- COMBINED STRESS ---------------- */

	public Map<String, Object> combinedStress(int durationSec) {
		int requested = durationSec;
		if (durationSec < 1)
			durationSec = 1;
		if (durationSec > 60) {
			log.warn("combinedStress: requested durationSec={} exceeds cap 60; clamping", durationSec);
			durationSec = 60;
		}

		int threads = Math.min(Runtime.getRuntime().availableProcessors() * 2, 12);
		log.info("combinedStress START durationSecRequested={} durationSecActual={} threads={}", requested, durationSec,
				threads);

		Instant deadline = Instant.now().plusSeconds(durationSec);
		ExecutorService pool = Executors.newFixedThreadPool(threads);

		AtomicInteger ops = new AtomicInteger();
		AtomicInteger cpuCalls = new AtomicInteger();
		AtomicInteger memCalls = new AtomicInteger();
		AtomicInteger dbCalls = new AtomicInteger();

		List<Future<?>> futures = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			futures.add(pool.submit(() -> {
				while (Instant.now().isBefore(deadline)) {
					cpuIntensive(100_000);
					cpuCalls.incrementAndGet();
					memoryIntensive(5);
					memCalls.incrementAndGet();
					databaseIntensive(20);
					dbCalls.incrementAndGet();
					ops.addAndGet(3);
				}
			}));
		}

		long started = System.currentTimeMillis();
		for (Future<?> f : futures) {
			try {
				f.get();
			} catch (Exception e) {
				log.error("combinedStress worker error", e);
			}
		}
		pool.shutdown();
		long totalDurationMs = System.currentTimeMillis() - started;

		log.info(
				"combinedStress DONE durationSecActual={} threads={} ops={} cpuCalls={} memCalls={} dbCalls={} durationMs={}",
				durationSec, threads, ops.get(), cpuCalls.get(), memCalls.get(), dbCalls.get(), totalDurationMs);

		return Map.of("type", "combined_stress", "threads", threads, "durationSeconds", durationSec, "operations",
				ops.get(), "cpuCalls", cpuCalls.get(), "memCalls", memCalls.get(), "dbCalls", dbCalls.get(),
				"totalDurationMs", totalDurationMs);
	}
}
