/*
 * LoadController
 * What this class does:
 *   - Maps HTTP endpoints to LoadService.
 *   - Logs each incoming request with parameters so you can correlate behavior.
 * Notes:
 *   - Still no logs in DB; logs are stdout only.
 */
package dev.harborwatch.load;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoadController {

	private static final Logger log = LoggerFactory.getLogger(LoadController.class);
	private final LoadService loadService;

	public LoadController(LoadService loadService) {
		this.loadService = loadService;
	}

	@GetMapping("/cpu-intensive")
	public Map<String, Object> cpu(@RequestParam(defaultValue = "1000000") int iterations) {
		log.info("HTTP GET /api/cpu-intensive iterations={}", iterations);
		return loadService.cpuIntensive(iterations);
	}

	@GetMapping("/memory-intensive")
	public Map<String, Object> memory(@RequestParam(name = "sizeMb", defaultValue = "10") int sizeMb) {
		log.info("HTTP GET /api/memory-intensive sizeMb={}", sizeMb);
		return loadService.memoryIntensive(sizeMb);
	}

	@GetMapping("/database-intensive")
	public Map<String, Object> database(@RequestParam(name = "ops", defaultValue = "200") int ops) {
		log.info("HTTP GET /api/database-intensive ops={}", ops);
		return loadService.databaseIntensive(ops);
	}

	@GetMapping("/combined-stress")
	public Map<String, Object> combined(@RequestParam(name = "durationSec", defaultValue = "15") int durationSec) {
		log.info("HTTP GET /api/combined-stress durationSec={}", durationSec);
		return loadService.combinedStress(durationSec);
	}
}
