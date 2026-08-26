package com.realtimeleaderboard.score;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(KafkaTestConfig.class)
class ScoreServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
