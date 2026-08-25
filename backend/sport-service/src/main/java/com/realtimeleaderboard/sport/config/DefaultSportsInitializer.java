package com.realtimeleaderboard.sport.config;

import com.realtimeleaderboard.sport.entity.Sport;
import com.realtimeleaderboard.sport.entity.SportCode;
import com.realtimeleaderboard.sport.repository.SportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the three supported sports on startup if (and only if) they are
 * missing. Idempotent: restarts never duplicate rows and existing data is
 * never modified or deleted. New sports are added as enum values + seed rows.
 */
@Component
public class DefaultSportsInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultSportsInitializer.class);

    private final SportRepository sportRepository;

    public DefaultSportsInitializer(SportRepository sportRepository) {
        this.sportRepository = sportRepository;
    }

    @Override
    public void run(String... args) {
        seedIfMissing(SportCode.FOOTBALL, "Football", "Association football (soccer) competitions");
        seedIfMissing(SportCode.CRICKET, "Cricket", "Cricket competitions");
        seedIfMissing(SportCode.F1, "Formula 1", "Formula 1 motor racing competitions");
    }

    private void seedIfMissing(SportCode code, String name, String description) {
        if (sportRepository.existsByCode(code)) {
            log.debug("Sport {} already present; skipping seed", code);
            return;
        }
        sportRepository.save(new Sport(code, name, description));
        log.info("Seeded default sport: {}", code);
    }
}
